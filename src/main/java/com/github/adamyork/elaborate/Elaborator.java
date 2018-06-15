package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;
import filter.ParserPatterns;
import filter.ParserPredicates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Logger LOG = LogManager.getLogger(Elaborator.class);

    private final String inputPath;
    private final String className;
    private final String methodName;
    private final String methodArgs;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> implicitMethod;

    Elaborator(final String inputPath,
               final String className,
               final String methodName,
               final String methodArgs,
               final List<String> includes,
               final List<String> excludes,
               final List<String> implicitMethods) {
        this.inputPath = inputPath;
        this.className = className;
        this.methodName = methodName;
        this.methodArgs = methodArgs;
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
        LOG.info("building invocation tree");
        return targetMetadata.map(metadata -> findInvocationsInMethod(metadata,
                classMetadataList, methodName, methodArgs))
                .or(() -> Optional.of(new ArrayList<>()))
                .get();
    }

    private List<MethodInvocation> findInvocationsInMethod(final ClassMetadata classMetadata,
                                                           final List<ClassMetadata> classMetadataList,
                                                           final String methodNameReference,
                                                           final String methodArgsReference) {
        LOG.debug("finding method invocations for " + classMetadata.getClassName() + " within method " + methodNameReference);
        final Pattern methodLocator = ParserPatterns.buildMethodLocatorPattern(methodNameReference, Optional.ofNullable(methodArgsReference));
        final Matcher methodLocatorMatcher = methodLocator.matcher(classMetadata.getClassContent());

        if (!methodLocatorMatcher.find()) {
            LOG.debug("no method named " + methodNameReference + " exists on class " + classMetadata.getClassName());
            return getSuperClassInvocations(classMetadata, classMetadataList, methodNameReference);
        }

        final String methodIndexToEof = classMetadata.getClassContent().substring(methodLocatorMatcher.start());

        final Pattern methodBodyEndPattern = Pattern.compile("^[\\s\\S]*?(?=\\n{2,})");
        final Matcher methodBodyEndMatcher = methodBodyEndPattern.matcher(methodIndexToEof);
        final Optional<String> maybeMethodIndexToEndOfMethod = maybeGetMethodContents(methodBodyEndMatcher, methodIndexToEof);

        if (!maybeMethodIndexToEndOfMethod.isPresent()) {
            LOG.debug("no body end found for " + methodNameReference + " on class " + classMetadata.getClassName());
            return new ArrayList<>();
        }

        final String methodIndexToEndOfMethod = maybeMethodIndexToEndOfMethod.get();

        final Pattern methodContentsStartPattern = Pattern.compile("Code:");
        final Matcher methodContentsStartMatcher = methodContentsStartPattern.matcher(methodIndexToEndOfMethod);

        if (!methodContentsStartMatcher.find()) {
            LOG.debug("no start of body contents found for " + methodNameReference + " on class " + classMetadata.getClassName());
            return new ArrayList<>();
        }

        final String methodBody = methodIndexToEndOfMethod.substring(methodContentsStartMatcher.start());
        final List<String> methodBodyLines = List.of(methodBody.split("\n"));
        final List<String> methodBodyLinesWithDynamicHoisted = mergeLambdaBodyLinesWithMethodLines(methodBodyLines,
                classMetadata, Pattern.compile("(?s)Code:.*?(?=\\n\\n)|(?s)Code:.*?(?=\\n})"));

        final List<String> filtered = filterNonEssentialInternalsFromMethodLines(methodBodyLinesWithDynamicHoisted
                , classMetadata);
        LOG.debug("found " + filtered.size() + " invocations");

        return filtered.stream()
                .map(line -> lineToMethodInvocation(line, classMetadata, classMetadataList))
                .collect(Collectors.toList());
    }

    private Optional<String> maybeGetMethodContents(final Matcher methodBodyEndMatcher, final String methodIndexToEof) {
        if (!methodBodyEndMatcher.find()) {
            final int lastReturn = methodIndexToEof.lastIndexOf("return");
            if (lastReturn != -1) {
                return Optional.of(methodIndexToEof.substring(0, lastReturn + "return".length()));
            }
            return Optional.empty();
        }
        return Optional.of(methodBodyEndMatcher.group());
    }

    private List<MethodInvocation> getSuperClassInvocations(final ClassMetadata classMetadata,
                                                            final List<ClassMetadata> classMetadataList,
                                                            final String methodNameReference) {
        if (!classMetadata.getSuperClass().isEmpty()) {
            LOG.debug("checking super class " + classMetadata.getClassName() + " for method");
            final Optional<ClassMetadata> superClassMetadata = classMetadataList.stream().filter(metadata -> {
                final int genericIndex = classMetadata.getSuperClass().indexOf("<");
                if (genericIndex == -1) {
                    return metadata.getClassName().equals(classMetadata.getSuperClass());
                }
                return metadata.getClassName().equals(classMetadata.getSuperClass().substring(0, genericIndex));
            }).findFirst();
            return superClassMetadata
                    .map(metadata -> findInvocationsInMethod(metadata, classMetadataList, methodNameReference,
                            ""))
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
        final String arguments = buildArgumentAsString(right);
        final String[] maybeMethodRefClassName = methodReference.split("\\.");
        final MethodInvocation methodInvocation;

        if (maybeMethodRefClassName.length == 1) {
            methodInvocation = new MethodInvocation.Builder(classMetadata.getClassName(), maybeMethodRefClassName[0], arguments, new ArrayList<>()).build();
        } else {
            final String callObjectClassName = maybeMethodRefClassName[0].replaceAll("/", ".");
            methodInvocation = new MethodInvocation.Builder(callObjectClassName, maybeMethodRefClassName[1], arguments, new ArrayList<>()).build();
        }

        final Optional<ClassMetadata> invocationClassMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                .findFirst();

        if (!invocationClassMetadata.isPresent()) {
            return methodInvocation;
        }

        final ClassMetadata maybeInterfaceClassMetadata = invocationClassMetadata.get();
        if (maybeInterfaceClassMetadata.isInterface()) {
            LOG.debug("interface " + maybeInterfaceClassMetadata.getClassName() + " found in invocation list");
            return processPossibleImplementations(classMetadataList, maybeInterfaceClassMetadata, methodInvocation, classMetadata);
        } else {
            LOG.debug("no interface object in invocation list");
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
                                                            final MethodInvocation methodInvocation,
                                                            final ClassMetadata parentClassMetadata) {
        final List<ClassMetadata> implementations = classMetadataList.stream()
                .filter(metadata -> metadata.getInterfaces().stream()
                        .anyMatch(impl -> {
                            final String genericsRemoved = impl.replaceAll("<.*", "");
                            return genericsRemoved.equals(maybeInterfaceClassMetadata.getClassName());
                        }))
                .filter(metadata -> !metadata.getClassName().equals(parentClassMetadata.getClassName()))
                .collect(Collectors.toList());
        final List<MethodInvocation> aggregateInvocations = implementations.stream()
                .map(impl -> {
                    final List<MethodInvocation> invocations = findInvocationsInMethod(impl, classMetadataList,
                            methodInvocation.getMethod(), methodInvocation.getArguments());
                    return new MethodInvocation.Builder(impl.getClassName(), methodInvocation.getMethod(),
                            methodInvocation.getArguments(), true, invocations).build();
                })
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
                    //TODO this this may need multi and single support
                    final Pattern lambdaRefContentPattern = ParserPatterns.buildSingleMethodBodyEndLocatorPattern();
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

    private List<String> filterNonEssentialInternalsFromMethodLines(final List<String> methodBodyLines,
                                                                    final ClassMetadata classMetadata) {
        return methodBodyLines.stream()
                .filter(ParserPredicates.invocationIsSupported())
                .map(line -> line.replaceAll("^.*invokevirtual.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokeinterface.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokespecial.*//Method", ""))
                .map(line -> hoistMethodReferences(line, classMetadata))
                .filter(ParserPredicates.invocationIsNotAlsoImplied(implicitMethod))
                .filter(ParserPredicates.invocationIsNotExcluded(excludes))
                .collect(Collectors.toList());
    }

    private String hoistMethodReferences(final String line, final ClassMetadata classMetadata) {
        final Pattern bootstrapMethodReferencePattern = Pattern.compile("InvokeDynamic#[0-9]+:");
        final Matcher bootstrapMethodReferenceMatcher = bootstrapMethodReferencePattern.matcher(line);
        if (bootstrapMethodReferenceMatcher.find()) {
            final String[] parts = bootstrapMethodReferenceMatcher.group().split("InvokeDynamic#");
            final String methodReference = classMetadata.getMethodReferences().get(parts[1].replace(":", ""));
            final String noRef = methodReference.replaceAll("#[0-9]+REF_", "");
            final String noVirtual = noRef.replace("invokeVirtual", "");
            final String noInterface = noVirtual.replace("invokeInterface", "");
            final String noStatic = noInterface.replace("invokeStatic", "");
            return noStatic.replace("invokeSpecial", "");
        }
        return line.replaceAll("^.*invokedynamic.*//InvokeDynamic#[0-9]+:", "a/dynamic/pkg/Lambda.");
    }

    private String buildArgumentAsString(final String input) {
        final List<String> individualArguments = Arrays.asList(input.split(";"));
        final String grouped = individualArguments.stream().map(arg -> {
            if (arg.contains(")")) {
                return arg.split("\\)")[0];
            }
            return arg;
        }).collect(Collectors.joining(";"));
        final List<String> groups = new ArrayList<>();
        groups.add(grouped);
        return groups.stream().map(arg -> {
            if (arg.indexOf("[L") == 0) {
                return arg.substring(2);
            }
            return arg;
        }).map(arg -> {
            if (arg.indexOf("L") == 0) {
                return arg.substring(1);
            }
            return arg;
        }).map(arg -> {
            if (arg.length() > 1) {
                if (arg.substring(arg.length() - 1).equals(";")) {
                    return arg.substring(0, arg.length() - 1);
                }
            }
            return arg;
        }).findFirst().orElse("");
    }

}
