package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.filter.ParserPatterns;
import com.github.adamyork.elaborate.filter.ParserPredicates;
import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
                classMetadataList, methodName, methodArgs, Optional.empty()).v1)
                .orElseGet(ArrayList::new);
    }

    private Tuple2<List<MethodInvocation>, List<String>> findInvocationsInMethod(final ClassMetadata classMetadata,
                                                                                 final List<ClassMetadata> classMetadataList,
                                                                                 final String methodNameReference,
                                                                                 final String methodArgsReference,
                                                                                 final Optional<List<String>> maybeDirectCallerChain) {
        final List<String> directCallerChain = maybeDirectCallerChain.orElse(new LinkedList<>());
        return Optional.of(isRecursionPresent(classMetadata, methodNameReference, methodArgsReference, directCallerChain))
                .filter(bool -> bool)
                .map(bool -> Tuple.tuple((List<MethodInvocation>) new ArrayList<MethodInvocation>(), directCallerChain))
                .orElseGet(() -> {
                    directCallerChain.add(classMetadata.getClassName() + "@@" + methodNameReference + "@@" + methodArgsReference);
                    LOG.debug("finding method invocations for " + classMetadata.getClassName() + " within method " + methodNameReference);
                    final Pattern methodLocator = ParserPatterns.buildMethodLocatorPattern(methodNameReference, Optional.ofNullable(methodArgsReference));
                    final Matcher methodLocatorMatcher = methodLocator.matcher(classMetadata.getClassContent());
                    final List<MethodInvocation> invocations = Optional.of(!methodLocatorMatcher.find())
                            .filter(bool -> bool)
                            .map(bool -> logState("no method named " + methodNameReference + " exists on class " + classMetadata.getClassName()))
                            .map(bool -> getSuperClassInvocations(classMetadata, classMetadataList, methodNameReference, directCallerChain))
                            .orElseGet(() -> parseMethodMatch(classMetadata, classMetadataList, methodNameReference,
                                    methodLocatorMatcher, directCallerChain));
                    return Tuple.tuple(invocations, directCallerChain);
                });
    }

    private boolean isRecursionPresent(final ClassMetadata classMetadata,
                                       final String methodNameReference,
                                       final String methodArgsReference,
                                       final List<String> directCallerChain) {
        return directCallerChain
                .stream()
                .anyMatch(previousCaller -> {
                    final String currentCaller = classMetadata.getClassName() + "@@" +
                            methodNameReference + "@@" + methodArgsReference;
                    return Optional.of(previousCaller.equals(currentCaller))
                            .filter(bool -> bool)
                            .map(bool -> {
                                LOG.warn("\n\nrecursion possibly detected for previous invoker " + previousCaller);
                                LOG.warn("when processing invocation for invoker " + currentCaller + "\n");
                                return true;
                            }).orElse(false);
                });
    }

    private List<MethodInvocation> parseMethodMatch(final ClassMetadata classMetadata,
                                                    final List<ClassMetadata> classMetadataList,
                                                    final String methodNameReference,
                                                    final Matcher methodLocatorMatcher,
                                                    final List<String> directCallerChain) {
        final String methodIndexToEof = classMetadata.getClassContent().substring(methodLocatorMatcher.start());
        final Pattern methodBodyEndPattern = ParserPatterns.buildMethodBodyEndLocatorPattern();
        final Matcher methodBodyEndMatcher = methodBodyEndPattern.matcher(methodIndexToEof);
        final Optional<String> maybeMethodIndexToEndOfMethod = maybeGetMethodContents(methodBodyEndMatcher, methodIndexToEof);
        return maybeMethodIndexToEndOfMethod
                .map(body -> parseMethodMatchBody(classMetadata, classMetadataList, methodNameReference, body, directCallerChain))
                .orElseGet(() -> logStateSupplier("no body end found for " + methodNameReference + " on class "
                        + classMetadata.getClassName(), new ArrayList<>()));
    }

    @SuppressWarnings("unchecked")
    private List<MethodInvocation> parseMethodMatchBody(final ClassMetadata classMetadata,
                                                        final List<ClassMetadata> classMetadataList,
                                                        final String methodNameReference,
                                                        final String methodIndexToEndOfMethod,
                                                        final List<String> directCallerChain) {
        final Pattern methodContentsStartPattern = Pattern.compile("Code:");
        final Matcher methodContentsStartMatcher = methodContentsStartPattern.matcher(methodIndexToEndOfMethod);
        return Optional.of(methodContentsStartMatcher.find())
                .filter(bool -> bool)
                .map(bool -> getMethodMatchBodyContents(classMetadata, classMetadataList,
                        methodIndexToEndOfMethod, methodContentsStartMatcher, directCallerChain))
                .orElseGet(() -> logStateSupplier("no start of body contents found for " + methodNameReference
                        + " on class " + classMetadata.getClassName(), new ArrayList()));

    }

    private List<MethodInvocation> getMethodMatchBodyContents(final ClassMetadata classMetadata,
                                                              final List<ClassMetadata> classMetadataList,
                                                              final String methodIndexToEndOfMethod,
                                                              final Matcher methodContentsStartMatcher,
                                                              final List<String> directCallerChain) {
        final String methodBody = methodIndexToEndOfMethod.substring(methodContentsStartMatcher.start());
        final List<String> methodBodyLines = Arrays.asList(methodBody.split("\n"));
        final List<String> filtered = filterNonEssentialInternalsFromMethodLines(methodBodyLines
                , classMetadata);
        return filtered.stream()
                .map(line -> lineToMethodInvocation(line, classMetadata, classMetadataList, directCallerChain))
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
                                                            final String methodNameReference,
                                                            final List<String> directCallerChain) {
        return Optional.of(!classMetadata.getSuperClass().isEmpty())
                .filter(bool -> bool)
                .map(bool -> {
                    LOG.debug("checking super class " + classMetadata.getClassName() + " for method");
                    return classMetadataList.stream()
                            .filter(metadata -> {
                                final int genericIndex = classMetadata.getSuperClass().indexOf("<");
                                return Optional.of(genericIndex == -1)
                                        .filter(noGenericIndex -> noGenericIndex)
                                        .map(noGenericIndex -> metadata.getClassName().equals(classMetadata.getSuperClass()))
                                        .orElseGet(() -> metadata.getClassName().equals(classMetadata.getSuperClass().substring(0, genericIndex)));
                            })
                            .findFirst()
                            .map(metadata -> findInvocationsInMethod(metadata, classMetadataList,
                                    methodNameReference, "", Optional.of(directCallerChain)).v1)
                            .orElseGet(ArrayList::new);
                }).orElseGet(ArrayList::new);
    }

    private MethodInvocation lineToMethodInvocation(final String line, final ClassMetadata classMetadata,
                                                    final List<ClassMetadata> classMetadataList,
                                                    final List<String> directCallerChain) {
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
                        .map(bool -> processPossibleImplementations(classMetadataList, metadata,
                                methodInvocation, classMetadata, directCallerChain))
                        .orElseGet(() -> {
                            LOG.debug("no interface object in invocation list");
                            final Tuple2<List<MethodInvocation>, List<String>> nestedInvocationsAndCallChains = findInvocationsInMethod(metadata,
                                    classMetadataList, methodInvocation.getMethod(),
                                    methodInvocation.getArguments(), Optional.of(directCallerChain));
                            final List<MethodInvocation> impliedMethodInvocations = processImpliedMethodInvocations(classMetadataList,
                                    methodInvocation, directCallerChain);
                            nestedInvocationsAndCallChains.v1.addAll(impliedMethodInvocations);
                            return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                                    methodInvocation.getArguments(), nestedInvocationsAndCallChains.v1).build();
                        }))
                .orElse(methodInvocation);
    }

    private MethodInvocation processPossibleImplementations(final List<ClassMetadata> classMetadataList,
                                                            final ClassMetadata maybeInterfaceClassMetadata,
                                                            final MethodInvocation methodInvocation,
                                                            final ClassMetadata parentClassMetadata,
                                                            final List<String> directCallerChain) {
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
                            methodInvocation.getMethod(), methodInvocation.getArguments(),
                            Optional.of(directCallerChain)).v1;
                    return new MethodInvocation.Builder(impl.getClassName(), methodInvocation.getMethod(),
                            methodInvocation.getArguments(), true, invocations).build();
                })
                .collect(Collectors.toList());
        return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                methodInvocation.getArguments(), aggregateInvocations).build();
    }

    private List<MethodInvocation> processImpliedMethodInvocations(final List<ClassMetadata> classMetadataList,
                                                                   final MethodInvocation methodInvocation,
                                                                   final List<String> directCallerChain) {
        return Optional.of(methodInvocation.getMethod().contains("<init>"))
                .filter(bool -> bool)
                .map(bool -> implicitMethod.stream()
                        .map(method -> {
                            final Optional<ClassMetadata> maybeNewObjectClassMetadata = classMetadataList.stream()
                                    .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                                    .findFirst();
                            return maybeNewObjectClassMetadata
                                    .map(newObjectClassMetadata -> findInvocationsInMethod(newObjectClassMetadata,
                                            classMetadataList, method, "",
                                            Optional.of(directCallerChain)).v1)
                                    .orElseGet(ArrayList::new);
                        })
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

    }

    private List<String> filterNonEssentialInternalsFromMethodLines(final List<String> methodBodyLines,
                                                                    final ClassMetadata classMetadata) {
        return methodBodyLines.stream()
                .filter(ParserPredicates.invocationIsSupported())
                .map(line -> locateAndLiftDynamicInvocations(line, classMetadata))
                .map(line -> line.replaceAll("^.*invokevirtual.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokeinterface.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//Method", ""))
                .map(line -> line.replaceAll("^.*invokestatic.*//InterfaceMethod", ""))
                .map(line -> line.replaceAll("^.*invokespecial.*//Method", ""))
                .filter(ParserPredicates.invocationIsNotAlsoImplied(implicitMethod))
                .filter(ParserPredicates.invocationIsNotExcluded(excludes))
                .collect(Collectors.toList());
    }

    private String locateAndLiftDynamicInvocations(final String line, final ClassMetadata classMetadata) {
        final Pattern bootstrapMethodRefDynamicPattern = Pattern.compile("InvokeDynamic#[0-9]+");
        final Matcher bootstrapMethodRefDynamicMatcher = bootstrapMethodRefDynamicPattern.matcher(line);
        final boolean isDynamic = bootstrapMethodRefDynamicMatcher.find();
        return Optional.of(isDynamic)
                .filter(bool -> bool)
                .map(bool -> {
                    final String[] parts = bootstrapMethodRefDynamicMatcher.group().split("InvokeDynamic#");
                    final Optional<String> methodReference = Optional.ofNullable(classMetadata.getMethodReferences()
                            .get(parts[1].replace(":", "")));
                    return methodReference.map(ref -> {
                        final String noRef = ref.replaceAll("#[0-9]+REF_", "");
                        final String noVirtual = noRef.replaceAll("invokeVirtual", "");
                        final String noInterface = noVirtual.replace("invokeInterface", "");
                        final String noStatic = noInterface.replace("invokeStatic", "");
                        return noStatic.replace("invokeSpecial", "");
                    }).orElse(line);
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
