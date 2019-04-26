package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;
import filter.ParserPatterns;
import filter.ParserPredicates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.tuple.Tuple;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
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

    @SuppressWarnings({"WeakerAccess"})
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
                .orElseGet(ArrayList::new);
    }

    private List<MethodInvocation> findInvocationsInMethod(final ClassMetadata classMetadata,
                                                           final List<ClassMetadata> classMetadataList,
                                                           final String methodNameReference,
                                                           final String methodArgsReference) {
        LOG.debug("finding method invocations for " + classMetadata.getClassName() + " within method " + methodNameReference);
        final Pattern methodLocator = ParserPatterns.buildMethodLocatorPattern(methodNameReference, Optional.ofNullable(methodArgsReference));
        final Matcher methodLocatorMatcher = methodLocator.matcher(classMetadata.getClassContent());
        return Optional.of(!methodLocatorMatcher.find())
                .filter(bool -> bool)
                .map(bool -> logState("no method named " + methodNameReference + " exists on class " + classMetadata.getClassName()))
                .map(bool -> getSuperClassInvocations(classMetadata, classMetadataList, methodNameReference))
                .orElseGet(() -> parseMethodMatch(classMetadata, classMetadataList, methodNameReference, methodLocatorMatcher));

    }

    private List<MethodInvocation> parseMethodMatch(final ClassMetadata classMetadata,
                                                    final List<ClassMetadata> classMetadataList,
                                                    final String methodNameReference,
                                                    final Matcher methodLocatorMatcher) {
        final String methodIndexToEof = classMetadata.getClassContent().substring(methodLocatorMatcher.start());
        final Pattern methodBodyEndPattern = Pattern.compile("^[\\s\\S]*?(?=\\n{2,})");
        final Matcher methodBodyEndMatcher = methodBodyEndPattern.matcher(methodIndexToEof);
        final Optional<String> maybeMethodIndexToEndOfMethod = maybeGetMethodContents(methodBodyEndMatcher, methodIndexToEof);
        return maybeMethodIndexToEndOfMethod
                .map(body -> parseMethodMatchBody(classMetadata, classMetadataList, methodNameReference, body))
                .orElseGet(() -> logStateSupplier("no body end found for " + methodNameReference + " on class "
                        + classMetadata.getClassName(), new ArrayList<>()));
    }

    @SuppressWarnings("unchecked")
    private List<MethodInvocation> parseMethodMatchBody(final ClassMetadata classMetadata,
                                                        final List<ClassMetadata> classMetadataList,
                                                        final String methodNameReference,
                                                        final String methodIndexToEndOfMethod) {
        final Pattern methodContentsStartPattern = Pattern.compile("Code:");
        final Matcher methodContentsStartMatcher = methodContentsStartPattern.matcher(methodIndexToEndOfMethod);
        return Optional.of(methodContentsStartMatcher.find())
                .filter(bool -> bool)
                .map(bool -> getMethodMatchBodyContents(classMetadata, classMetadataList,
                        methodIndexToEndOfMethod, methodContentsStartMatcher))
                .orElseGet(() -> logStateSupplier("no start of body contents found for " + methodNameReference
                        + " on class " + classMetadata.getClassName(), new ArrayList()));

    }

    private List<MethodInvocation> getMethodMatchBodyContents(final ClassMetadata classMetadata,
                                                              final List<ClassMetadata> classMetadataList,
                                                              final String methodIndexToEndOfMethod,
                                                              final Matcher methodContentsStartMatcher) {
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
        return Optional.of(!methodBodyEndMatcher.find())
                .filter(bool -> bool)
                .map(bool -> {
                    final int lastReturn = methodIndexToEof.lastIndexOf("return");
                    return Optional.of(lastReturn != -1)
                            .filter(hasLastReturn -> hasLastReturn)
                            .map(hasLastReturn -> methodIndexToEof.substring(0, lastReturn + "return".length()));
                })
                .orElseGet(() -> Optional.of(methodBodyEndMatcher.group()));
    }

    private List<MethodInvocation> getSuperClassInvocations(final ClassMetadata classMetadata,
                                                            final List<ClassMetadata> classMetadataList,
                                                            final String methodNameReference) {
        return Optional.of(!classMetadata.getSuperClass().isEmpty())
                .filter(bool -> bool)
                .map(bool -> {
                    LOG.debug("checking super class " + classMetadata.getClassName() + " for method");
                    return classMetadataList.stream()
                            .filter(metadata -> {
                                final int genericIndex = classMetadata.getSuperClass().indexOf("<");
                                return Optional.of(genericIndex == -1)
                                        .filter(noGenericIndex -> noGenericIndex)
                                        .map(noGenericIndex -> Tuple.tuple(genericIndex, metadata.getClassName()
                                                .equals(classMetadata.getSuperClass())))
                                        .orElseGet(() -> Tuple.tuple(genericIndex, metadata.getClassName()
                                                .equals(classMetadata.getSuperClass()
                                                        .substring(0, genericIndex)))).v2;
                            })
                            .findFirst()
                            .map(metadata -> findInvocationsInMethod(metadata, classMetadataList,
                                    methodNameReference, ""))
                            .orElseGet(ArrayList::new);
                }).orElseGet(ArrayList::new);
    }

    private MethodInvocation lineToMethodInvocation(final String line, final ClassMetadata classMetadata,
                                                    final List<ClassMetadata> classMetadataList) {
        final String normalizedLine = line.replace("\"[L", "").replace(";\"", "");
        final String[] parts = normalizedLine.split(":\\(");
        final String methodReference = parts[0];
        final String right = parts[1];
        final String arguments = buildArgumentAsString(right);
        final String[] maybeMethodRefClassName = methodReference.split("\\.");
        final MethodInvocation methodInvocation = Optional.of(maybeMethodRefClassName.length == 1)
                .filter(bool -> bool)
                .map(bool -> new MethodInvocation.Builder(classMetadata.getClassName(), maybeMethodRefClassName[0],
                        arguments, new ArrayList<>()).build())
                .orElseGet(() -> {
                    final String callObjectClassName = maybeMethodRefClassName[0].replaceAll("/", ".");
                    return new MethodInvocation.Builder(callObjectClassName, maybeMethodRefClassName[1],
                            arguments, new ArrayList<>()).build();
                });
        final Optional<ClassMetadata> invocationClassMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                .findFirst();
        return invocationClassMetadata
                .map(metadata -> Optional.of(metadata.isInterface())
                        .filter(bool -> bool)
                        .map(bool -> logState("interface " + metadata.getClassName() + " found in invocation list"))
                        .map(bool -> processPossibleImplementations(classMetadataList, metadata, methodInvocation, classMetadata))
                        .orElseGet(() -> {
                            LOG.debug("no interface object in invocation list");
                            final List<MethodInvocation> nestedInvocations = findInvocationsInMethod(metadata,
                                    classMetadataList, methodInvocation.getMethod(),
                                    methodInvocation.getArguments());
                            final List<MethodInvocation> impliedMethodInvocations = processImpliedMethodInvocations(classMetadataList,
                                    methodInvocation);
                            nestedInvocations.addAll(impliedMethodInvocations);
                            return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                                    methodInvocation.getArguments(), nestedInvocations).build();
                        }))
                .orElse(methodInvocation);
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
        return Optional.of(methodInvocation.getMethod().contains("<init>"))
                .filter(bool -> bool)
                .map(bool -> implicitMethod.stream()
                        .map(method -> {
                            final Optional<ClassMetadata> maybeNewObjectClassMetadata = classMetadataList.stream()
                                    .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                                    .findFirst();
                            return maybeNewObjectClassMetadata
                                    .map(newObjectClassMetadata -> findInvocationsInMethod(newObjectClassMetadata,
                                            classMetadataList, method, ""))
                                    .orElseGet(ArrayList::new);
                        })
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private List<String> mergeLambdaBodyLinesWithMethodLines(final List<String> methodBodyLines,
                                                             final ClassMetadata classMetadata,
                                                             final Pattern methodBodyLocator) {
        return methodBodyLines.stream()
                .map(line -> {
                    final Pattern lambdaPattern = Pattern.compile("^.*invokedynamic.*//InvokeDynamic#([0-9]+):");
                    final Matcher lambdaMatcher = lambdaPattern.matcher(line);
                    return Optional.of(!lambdaMatcher.find())
                            .filter(bool -> bool)
                            .map(bool -> new ArrayList<>(Arrays.asList(line)))
                            .orElseGet(() -> {
                                final String lambda = lambdaMatcher.group(1);
                                final Pattern lambdaReplacementPattern = Pattern.compile(".*lambda\\$.*\\$" + lambda);
                                final Matcher lambdaReplacementMatcher = lambdaReplacementPattern
                                        .matcher(classMetadata.getClassContent());
                                return Optional.of(!lambdaReplacementMatcher.find())
                                        .filter(bool -> bool)
                                        .map(bool -> new ArrayList<>(Arrays.asList(line)))
                                        .orElseGet(() -> {
                                            final String lambdaIndexToEof = classMetadata.getClassContent()
                                                    .substring(lambdaReplacementMatcher.start());
                                            final Matcher lambdaBodyMatcher = methodBodyLocator.matcher(lambdaIndexToEof);
                                            return Optional.of(!lambdaBodyMatcher.find())
                                                    .filter(bool -> bool)
                                                    .map(bool -> new ArrayList<>(Arrays.asList(line)))
                                                    .orElseGet(() -> {
                                                        final String lambdaBody = lambdaBodyMatcher.group();
                                                        //TODO this this may need multi and single support
                                                        final Pattern lambdaRefContentPattern = ParserPatterns
                                                                .buildSingleMethodBodyEndLocatorPattern();
                                                        final Matcher lambdaRefContentBody = lambdaRefContentPattern.matcher(lambdaBody);
                                                        return Optional.of(lambdaRefContentBody.find())
                                                                .filter(bool -> bool)
                                                                .map(bool -> {
                                                                    final String lambdaRefMethodBlock = lambdaBody.substring(0, lambdaRefContentBody.end());
                                                                    final List<String> lambdaRefMethodBlockLines = List.of(lambdaRefMethodBlock.split("\n"));
                                                                    return new ArrayList<>(lambdaRefMethodBlockLines);
                                                                })
                                                                .orElseGet(ArrayList::new);
                                                    });
                                        });
                            });
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
        return Optional.of(bootstrapMethodReferenceMatcher.find())
                .filter(bool -> bool)
                .map(bool -> {
                    final String[] parts = bootstrapMethodReferenceMatcher.group().split("InvokeDynamic#");
                    final String methodReference = classMetadata.getMethodReferences().get(parts[1].replace(":", ""));
                    final String noRef = methodReference.replaceAll("#[0-9]+REF_", "");
                    final String noVirtual = noRef.replace("invokeVirtual", "");
                    final String noInterface = noVirtual.replace("invokeInterface", "");
                    final String noStatic = noInterface.replace("invokeStatic", "");
                    return noStatic.replace("invokeSpecial", "");
                })
                .orElse(line.replaceAll("^.*invokedynamic.*//InvokeDynamic#[0-9]+:", "a/dynamic/pkg/Lambda."));
    }

    private String buildArgumentAsString(final String input) {
        final List<String> individualArguments = Arrays.asList(input.split(";"));
        final String grouped = individualArguments.stream()
                .map(arg -> Optional.of(arg.contains(")"))
                        .filter(bool -> bool)
                        .map(bool -> arg.split("\\)")[0])
                        .orElse(arg))
                .collect(Collectors.joining(";"));
        return Optional.of(grouped)
                .map(groupedStr -> Optional.of(groupedStr.indexOf("[L") == 0)
                        .filter(bool -> bool)
                        .map(bool -> groupedStr.substring(2))
                        .orElse(groupedStr))
                .map(groupedStr -> Optional.of(groupedStr.indexOf("L") == 0)
                        .filter(bool -> bool)
                        .map(bool -> groupedStr.substring(1))
                        .orElse(groupedStr))
                .map(groupedStr -> Optional.of(groupedStr.length() > 1)
                        .filter(bool -> bool)
                        .map(bool -> Optional.of(groupedStr.substring(groupedStr.length() - 1).equals(";"))
                                .filter(bool2 -> bool2)
                                .map(bool2 -> groupedStr.substring(0, groupedStr.length() - 1))
                                .orElse(groupedStr))
                        .orElse(groupedStr))
                .orElse("");
    }

    private boolean logState(final String message) {
        LOG.debug(message);
        return true;
    }

    @SuppressWarnings("SameParameterValue")
    private <T> T logStateSupplier(final String message, final T retVal) {
        LOG.debug(message);
        return retVal;
    }

}
