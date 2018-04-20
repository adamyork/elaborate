package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;
import filter.ParserPatterns;
import filter.ParserPredicates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class Elaborator {

    private final String inputPath;
    private final String className;
    private final String methodName;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> implicitMethod;

    Elaborator(final String inputPath,
               final String className,
               final String methodName,
               final List<String> includes,
               final List<String> excludes,
               final List<String> implicitMethods) {
        this.inputPath = inputPath;
        this.className = className;
        this.methodName = methodName;
        this.includes = includes;
        this.excludes = excludes;
        this.implicitMethod = implicitMethods;
    }

    @SuppressWarnings("WeakerAccess")
    public List<MethodInvocation> run() {
        final File source = new File(inputPath);
        final Parser parser = new Parser();
        final List<ClassMetadata> classMetadataList = parser.parse(source, includes);
        final Optional<ClassMetadata> targetMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(className))
                .findFirst();
        return targetMetadata.map(metadata -> findInvocationsInMethod(metadata,
                classMetadataList, methodName, ""))
                .or(() -> Optional.of(new ArrayList<>()))
                .get();
    }

    private List<MethodInvocation> findInvocationsInMethod(final ClassMetadata classMetadata,
                                                           final List<ClassMetadata> classMetadataList,
                                                           final String methodNameReference,
                                                           final String methodArgsReference) {
        System.out.println("finding method invocations for " + classMetadata.getClassName() + " within method " + methodNameReference);
        final Pattern methodLocator = ParserPatterns.buildMethodLocatorPattern(methodNameReference, Optional.ofNullable(methodArgsReference));
        final Matcher methodLocatorMatcher = methodLocator.matcher(classMetadata.getClassContent());

        if (!methodLocatorMatcher.find()) {
            System.out.println("no method named " + methodNameReference + " exists on class " + classMetadata.getClassName());
            return getSuperClassInvocations(classMetadata, classMetadataList, methodNameReference);
        }

        final String methodIndexToEof = classMetadata.getClassContent().substring(methodLocatorMatcher.start());
        final Pattern methodBodyLocator = Pattern.compile("(?s)Code:.*?(?=\\n\\n)|(?s)Code:.*?(?=\\n})");
        final Matcher methodBodyMatcher = methodBodyLocator.matcher(methodIndexToEof);

        if (!methodBodyMatcher.find()) {
            System.out.println("no body found for " + methodNameReference + " on class " + classMetadata.getClassName());
            return new ArrayList<>();
        }

        final Pattern methodBodyEndLocator = ParserPatterns.buildMethodBodyEndLocatorPattern();
        final String methodBodyToEof = methodBodyMatcher.group();
        final Matcher methodBodyEndMatcher = methodBodyEndLocator.matcher(methodBodyToEof);

        if (!methodBodyEndMatcher.find()) {
            System.out.println("no end of body found for " + methodNameReference + " on class " + classMetadata.getClassName());
            return new ArrayList<>();
        }

        final String methodBody = methodBodyToEof.substring(0, methodBodyEndMatcher.end());
        final List<String> methodBodyLines = List.of(methodBody.split("\n"));
        final List<String> methodBodyLinesWithDynamicHoisted = mergeLambdaBodyLinesWithMethodLines(methodBodyLines,
                classMetadata, methodBodyLocator);

        final List<String> filtered = filterNonEssentialInternalsFromMethodLines(methodBodyLinesWithDynamicHoisted);
        System.out.println("found " + filtered.size() + " invocations");

        return filtered.stream()
                .map(line -> lineToMethodInvocation(line, classMetadata, classMetadataList))
                .collect(Collectors.toList());
    }

    private List<MethodInvocation> getSuperClassInvocations(final ClassMetadata classMetadata,
                                                            final List<ClassMetadata> classMetadataList,
                                                            final String methodNameReference) {
        if (!classMetadata.getSuperClass().isEmpty()) {
            System.out.println("checking super class " + classMetadata.getClassName() + " for method");
            final Optional<ClassMetadata> superClassMetadata = classMetadataList.stream().filter(metadata -> {
                final int genericIndex = classMetadata.getSuperClass().indexOf("<");
                if (genericIndex == -1) {
                    return metadata.getClassName().equals(classMetadata.getSuperClass());
                }
                return metadata.getClassName().equals(classMetadata.getSuperClass().substring(0, genericIndex));
            }).findFirst();
            return superClassMetadata
                    .map(metadata -> findInvocationsInMethod(metadata, classMetadataList, methodNameReference, ""))
                    .or(() -> Optional.of(new ArrayList<>())).get();
        }
        return new ArrayList<>();
    }

    private MethodInvocation lineToMethodInvocation(final String line, final ClassMetadata classMetadata,
                                                    final List<ClassMetadata> classMetadataList) {
        final String normalizedLine = line.replace("\"[L", "").replace(";\"", "");
        final String[] parts = normalizedLine.split(":\\(");
        final String methodReference = parts[0];
        final String right = parts[1];
        final String[] argumentsParts = right.split(";\\)|;Z\\)");
        final String arguments = trimArguments(argumentsParts);

        final String[] maybeArguments = methodReference.split("\\.");

        final MethodInvocation methodInvocation;
        if (maybeArguments.length == 1) {
            methodInvocation = new MethodInvocation.Builder(classMetadata.getClassName(), maybeArguments[0], arguments).build();
        } else {
            final String callObjectClassName = maybeArguments[0].replaceAll("/", ".");
            methodInvocation = new MethodInvocation.Builder(callObjectClassName, maybeArguments[1], arguments).build();
        }

        final Optional<ClassMetadata> invocationClassMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                .findFirst();

        if (!invocationClassMetadata.isPresent()) {
            return methodInvocation;
        }

        final ClassMetadata maybeInterfaceClassMetadata = invocationClassMetadata.get();
        if (maybeInterfaceClassMetadata.isInterface()) {
            System.out.println("interface " + maybeInterfaceClassMetadata.getClassName() + " found in invocation list");
            return processPossibleImplementations(classMetadataList, maybeInterfaceClassMetadata, methodInvocation);
        } else {
            System.out.println("no interface object in invocation list");
            final List<MethodInvocation> nestedInvocations = findInvocationsInMethod(maybeInterfaceClassMetadata,
                    classMetadataList, methodInvocation.getMethod(),
                    methodInvocation.getArguments());
            nestedInvocations.addAll(processImpliedMethodInvocations(classMetadataList, methodInvocation));
            return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                    methodInvocation.getArguments(), nestedInvocations).build();
        }
    }

    private MethodInvocation processPossibleImplementations(final List<ClassMetadata> classMetadataList,
                                                            final ClassMetadata maybeInterfaceClassMetadata,
                                                            final MethodInvocation methodInvocation) {
        final List<ClassMetadata> implementations = classMetadataList.stream()
                .filter(metadata -> metadata.getInterfaces().stream()
                        .anyMatch(impl -> {
                            return impl.contains(maybeInterfaceClassMetadata.getClassName());
                        })).collect(Collectors.toList());
        final List<MethodInvocation> aggregateInvocations = implementations.stream()
                .map(impl -> findInvocationsInMethod(impl, classMetadataList, methodInvocation.getMethod(),
                        methodInvocation.getArguments()))
                .flatMap(List::stream)
                .map(impl -> new MethodInvocation.Builder(impl.getType(), impl.getMethod(),
                        impl.getArguments(), impl.getMethodInvocations()).discreet(false).build())
                .collect(Collectors.toList());
        return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                methodInvocation.getArguments(), aggregateInvocations).build();
    }

    private List<MethodInvocation> processImpliedMethodInvocations(final List<ClassMetadata> classMetadataList,
                                                                   final MethodInvocation methodInvocation) {
        if (!methodInvocation.getMethod().contains("<init>")) {
            return new ArrayList<>();
        }
        return implicitMethod.stream().map(method -> {
            final Optional<ClassMetadata> maybeNewObjectClassMetadata = classMetadataList.stream()
                    .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                    .findFirst();
            return maybeNewObjectClassMetadata
                    .map(newObjectClassMetadata -> findInvocationsInMethod(newObjectClassMetadata,
                            classMetadataList, method, ""))
                    .orElse(null);
        }).filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<String> mergeLambdaBodyLinesWithMethodLines(final List<String> methodBodyLines,
                                                             final ClassMetadata classMetadata,
                                                             final Pattern methodBodyLocator) {
        return methodBodyLines.stream()
                .map(line -> {
                    final List<String> mergedLines = new ArrayList<>();
                    final Pattern lambdaPattern = Pattern.compile("^.*invokedynamic.*//InvokeDynamic#([0-9]+):");
                    final Matcher lambdaMatcher = lambdaPattern.matcher(line);
                    if (!lambdaMatcher.find()) {
                        mergedLines.add(line);
                        return mergedLines;
                    }

                    final String lambda = lambdaMatcher.group(1);
                    final Pattern lambdaReplacementPattern = Pattern.compile(".*lambda\\$.*\\$" + lambda);
                    final Matcher lambdaReplacementMatcher = lambdaReplacementPattern
                            .matcher(classMetadata.getClassContent());

                    if (!lambdaReplacementMatcher.find()) {
                        mergedLines.add(line);
                        return mergedLines;
                    }

                    final String lambdaIndexToEof = classMetadata.getClassContent()
                            .substring(lambdaReplacementMatcher.start());
                    final Matcher lambdaBodyMatcher = methodBodyLocator.matcher(lambdaIndexToEof);

                    if (!lambdaBodyMatcher.find()) {
                        mergedLines.add(line);
                        return mergedLines;
                    }

                    final String lambdaBody = lambdaBodyMatcher.group();
                    final Pattern lambdaRefContentPattern = ParserPatterns.buildMethodBodyEndLocatorPattern();
                    final Matcher lambdaRefContentBody = lambdaRefContentPattern.matcher(lambdaBody);
                    if (lambdaRefContentBody.find()) {
                        final String lambdaRefMethodBlock = lambdaBody.substring(0, lambdaRefContentBody.end());
                        final List<String> lambdaRefMethodBlockLines = List.of(lambdaRefMethodBlock.split("\n"));
                        mergedLines.addAll(lambdaRefMethodBlockLines);
                    }
                    return mergedLines;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<String> filterNonEssentialInternalsFromMethodLines(final List<String> methodBodyLines) {
        return methodBodyLines.stream()
                .filter(ParserPredicates.invocationIsSupported())
                .map(line -> line.replaceAll("^.*invokevirtual.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokeinterface.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokespecial.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokedynamic.*//InvokeDynamic#[0-9]+:", "a/dynamic/pkg/Lamda."))
                .filter(ParserPredicates.invocationIsNotAlsoImplied(implicitMethod))
                .filter(ParserPredicates.invocationIsNotExcluded(excludes))
                .collect(Collectors.toList());
    }

    private String trimArguments(final String[] argumentParts) {
        if (argumentParts.length != 1) {
            return argumentParts[0].substring(1);
        }
        return "";
    }

}
