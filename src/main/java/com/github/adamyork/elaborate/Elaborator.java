package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;

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
@SuppressWarnings("WeakerAccess")
public class Elaborator {

    private final String inputPath;
    private final String className;
    private final String methodName;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> implicitMethod;

    public Elaborator(final String inputPath,
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

    public List<MethodInvocation> run() {
        final File source = new File(inputPath);
        final Parser parser = new Parser();
        final List<ClassMetadata> classMetadataList = parser.parse(source, includes);
        final Optional<ClassMetadata> targetMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(className))
                .findFirst();
        if (targetMetadata.isPresent()) {
            return findInnerCallsInMethod(targetMetadata.get(), classMetadataList, methodName, "");
        } else {
            return new ArrayList<>();
        }
    }

    private List<MethodInvocation> findInnerCallsInMethod(final ClassMetadata classMetadata,
                                                          final List<ClassMetadata> classMetadataList,
                                                          final String methodNameReference,
                                                          final String methodArgsReference) {
        System.out.println("className " + classMetadata.getClassName() + " meth ref " + methodNameReference);
        Pattern pattern = Pattern.compile(methodNameReference + "\\(.*\\);");
        if (!methodArgsReference.isEmpty()) {
            final String replaced = methodArgsReference.replace("/", ".")
                    .replace(";IL", ",int,")
                    .replace(";L", ",")
                    .replace(";", ",");
            final List<String> individualArguments = List.of(replaced.split(","));
            final String zzz = individualArguments.stream().map(arg -> {
                return ".*" + arg + ".*";
            }).collect(Collectors.joining(","));
            pattern = Pattern.compile(methodNameReference + "\\(" + zzz + "\\);");
        }
        final Matcher matcher = pattern.matcher(classMetadata.getClassContent());
        if (matcher.find()) {
            final String sub = classMetadata.getClassContent().substring(matcher.start());
            final Pattern pattern2 = Pattern.compile("(?s)Code:.*?(?=\\n\\n)|(?s)Code:.*?(?=\\n})");
            final Matcher matcher2 = pattern2.matcher(sub);
            if (matcher2.find()) {
                Pattern pattern3 = Pattern.compile("return$", Pattern.MULTILINE);
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pattern3 = Pattern.compile("return\\s", Pattern.MULTILINE);
                }
                final String found = matcher2.group();
                final Matcher matcher3 = pattern3.matcher(found);
                if (matcher3.find()) {
                    final String methodBlock = found.substring(0, matcher3.end());
                    final List<String> lines = List.of(methodBlock.split("\n"));
                    final List<String> linesWithDynamicHoisted = lines.stream()
                            .map(line -> {
                                final List<String> referenceLines = new ArrayList<>();
                                final Pattern lambdaRefPattern = Pattern.compile("^.*invokedynamic.*//InvokeDynamic#([0-9]+):");
                                final Matcher lambdaRefMatcher = lambdaRefPattern.matcher(line);
                                if (lambdaRefMatcher.find()) {
                                    final String lambdaRef = lambdaRefMatcher.group(1);
                                    final Pattern lambdaReplacementPattern = Pattern.compile(".*lambda\\$.*\\$" + lambdaRef);
                                    final Matcher lambdaReplacementMatcher = lambdaReplacementPattern.matcher(classMetadata.getClassContent());
                                    if (lambdaReplacementMatcher.find()) {
                                        final String lambdaStartAndEof = classMetadata.getClassContent().substring(lambdaReplacementMatcher.start());
                                        final Matcher lambdaRefContentMatcher = pattern2.matcher(lambdaStartAndEof);
                                        if (lambdaRefContentMatcher.find()) {
                                            final String lambdaRefContent = lambdaRefContentMatcher.group();
                                            Pattern lambdaRefContentPattern = Pattern.compile("return$", Pattern.MULTILINE);
                                            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                                                lambdaRefContentPattern = Pattern.compile("return\\s", Pattern.MULTILINE);
                                            }
                                            final Matcher lambdaRefContentBody = lambdaRefContentPattern.matcher(lambdaRefContent);
                                            if (lambdaRefContentBody.find()) {
                                                final String lambdaRefMethodBlock = lambdaRefContent.substring(0, lambdaRefContentBody.end());
                                                final List<String> lambdaRefMethodBlockLines = List.of(lambdaRefMethodBlock.split("\n"));
                                                referenceLines.addAll(lambdaRefMethodBlockLines);
                                            }
                                        }
                                    } else {
                                        referenceLines.add(line);
                                    }
                                } else {
                                    referenceLines.add(line);
                                }
                                return referenceLines;
                            })
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    final List<String> filtered = linesWithDynamicHoisted.stream()
                            .filter(line -> line.contains("invokevirtual") || line.contains("invokeinterface")
                                    || line.contains("invokestatic") || line.contains("invokespecial") || line.contains("invokedynamic"))
                            .map(line -> line.replaceAll("^.*invokevirtual.*//Method", ""))
                            .map(line -> line.replaceAll("^.*invokeinterface.*//InterfaceMethod", ""))
                            .map(line -> line.replaceAll("^.*invokestatic.*//Method", ""))
                            .map(line -> line.replaceAll("^.*invokestatic.*//InterfaceMethod", ""))
                            .map(line -> line.replaceAll("^.*invokespecial.*//Method", ""))
                            .map(line -> line.replaceAll("^.*invokedynamic.*//InvokeDynamic#[0-9]+:", "a/dynamic/pkg/Lamda."))
                            .filter(line -> {
                                try {
                                    final String left = line.split(":")[0];
                                    final String selfInvocation = left.split("\\.")[1];
                                    final boolean selfInvoc = implicitMethod.stream().noneMatch(include -> include.equals(selfInvocation));
                                    if (selfInvoc == false) {
                                        System.out.println("filtered because self invoc " + line);
                                    }
                                    return selfInvoc;
                                } catch (final IndexOutOfBoundsException exception) {
                                    return true;
                                }
                            })
                            .filter(line -> {
                                final String normalized = line.replace("/", ".").split(":")[0];
                                return excludes.stream().noneMatch(exclude -> {
                                    final Pattern excludePattern = Pattern.compile(exclude);
                                    final Matcher excludeMatcher = excludePattern.matcher(normalized);
                                    return excludeMatcher.find();
                                });
                            })
                            .collect(Collectors.toList());
                    System.out.println("found " + filtered.size() + " invocations");
                    final List<MethodInvocation> invocs = filtered.stream().map(line -> {
                        final String normalizedLine = line.replace("\"[L", "").replace(";\"", "");
                        final String[] parts = normalizedLine.split(":\\(");
                        final String methodReference = parts[0];
                        final String right = parts[1];
                        final String[] argumentsParts = right.split(";\\)|;Z\\)");
                        String arguments = "";
                        if (argumentsParts.length != 1) {
                            arguments = argumentsParts[0].substring(1);
                        }
                        final String[] subParts = methodReference.split("\\.");
                        final MethodInvocation methodInvocation;
                        if (subParts.length == 1) {
                            methodInvocation = new MethodInvocation.Builder(classMetadata.getClassName(), subParts[0], arguments).build();
                        } else {
                            final String callObjectClassName = subParts[0].replaceAll("/", ".");
                            methodInvocation = new MethodInvocation.Builder(callObjectClassName, subParts[1], arguments).build();
                        }
                        final Optional<ClassMetadata> invocationClassMetadata = classMetadataList.stream()
                                .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                                .findFirst();
                        if (invocationClassMetadata.isPresent()) {
                            final ClassMetadata maybeInterfaceClassMetadata = invocationClassMetadata.get();
                            if (maybeInterfaceClassMetadata.isInterface()) {
                                System.out.println("interface found in invocation list");
                                final List<ClassMetadata> implementations = classMetadataList.stream().filter(metadata -> {
                                    return metadata.getInterfaces().stream().anyMatch(impl -> {
                                        return impl.contains(maybeInterfaceClassMetadata.getClassName());
                                    });
                                }).collect(Collectors.toList());
                                final List<MethodInvocation> aggregateInvocations = implementations.stream().map(impl -> {
                                    return findInnerCallsInMethod(impl, classMetadataList, methodInvocation.getMethod(), methodInvocation.getArguments());
                                }).flatMap(List::stream).collect(Collectors.toList());
                                return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                                        methodInvocation.getArguments(), aggregateInvocations).build();
                            } else {
                                System.out.println("no interface object in invocation list");
                                final List<MethodInvocation> tmp = findInnerCallsInMethod(maybeInterfaceClassMetadata, classMetadataList, methodInvocation.getMethod(),
                                        methodInvocation.getArguments());
                                if (methodInvocation.getMethod().contains("<init>")) {
                                    final List<MethodInvocation> impliedInvocations = implicitMethod.stream().map(method -> {
                                        final Optional<ClassMetadata> newObjectClassMetadata = classMetadataList.stream().filter(metadata -> {
                                            return metadata.getClassName().equals(methodInvocation.getType());
                                        }).findFirst();
                                        if (newObjectClassMetadata.isPresent()) {
                                            return findInnerCallsInMethod(newObjectClassMetadata.get(), classMetadataList, method, "");
                                        }
                                        return null;
                                    }).filter(Objects::nonNull)
                                            .flatMap(List::stream)
                                            .collect(Collectors.toList());
                                    tmp.addAll(impliedInvocations);
                                }
                                return new MethodInvocation.Builder(methodInvocation.getType(), methodInvocation.getMethod(),
                                        methodInvocation.getArguments(), tmp).build();
                            }
                        }
                        return methodInvocation;
                    }).collect(Collectors.toList());
                    System.out.println("done processing className " + classMetadata.getClassName() + " meth ref " + methodNameReference);
                    return invocs;
                }
            }
        }
        System.out.println("no method named " + methodNameReference + " exists on class");
        List<MethodInvocation> maybeSuperClassInvocations = new ArrayList<>();
        if (!classMetadata.getSuperClass().isEmpty()) {
            System.out.println("checking super class for method");
            final Optional<ClassMetadata> superClassMetadata = classMetadataList.stream().filter(metadata -> {
                final int genericIndex = classMetadata.getSuperClass().indexOf("<");
                String className;
                try {
                    className = classMetadata.getSuperClass().substring(0, genericIndex);
                } catch (final StringIndexOutOfBoundsException exception) {
                    className = classMetadata.getSuperClass();
                }
                return metadata.getClassName().equals(className);
            }).findFirst();
            if (superClassMetadata.isPresent()) {
                maybeSuperClassInvocations = findInnerCallsInMethod(superClassMetadata.get(), classMetadataList, methodNameReference, "");
                System.out.println("");
            }
        }
        return maybeSuperClassInvocations;
    }

}
