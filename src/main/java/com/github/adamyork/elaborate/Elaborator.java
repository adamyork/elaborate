package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MethodInvocation;
import com.github.adamyork.elaborate.parser.ArchiveParser;
import com.github.adamyork.elaborate.parser.DirParser;
import com.github.adamyork.elaborate.parser.Parser;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class Elaborator {

    private final String inputPath;
    private final String className;
    private final String methodName;
    private final Map<Boolean, Parser> parserMap;

    public Elaborator(final String inputPath, final String className, final String methodName) {
        this.inputPath = inputPath;
        this.className = className;
        this.methodName = methodName;
        parserMap = new HashMap<>();
        parserMap.put(true, new ArchiveParser());
        parserMap.put(false, new DirParser());
    }

    public List<MethodInvocation> run() {
        final File source = new File(inputPath);
        final boolean isArchive = inputPath.contains(".jar") || inputPath.contains(".war");
        final List<ClassMetadata> classMetadataList = parserMap.get(isArchive).parse(source, inputPath);
        final Optional<ClassMetadata> targetMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(className))
                .findFirst();
        if (targetMetadata.isPresent()) {
            return findInnerCallsInMethod(targetMetadata.get(), classMetadataList, methodName, "");
        } else {
            return new ArrayList<>();
        }
    }

    public List<MethodInvocation> findInnerCallsInMethod(final ClassMetadata classMetadata,
                                                         final List<ClassMetadata> classMetadataList,
                                                         final String methodNameReference,
                                                         final String methodArgsReference) {
        System.out.println("className " + classMetadata.getClassName() + " meth ref " + methodNameReference);
        Pattern pattern = Pattern.compile(methodNameReference + "\\(.*\\);");
        if (!methodArgsReference.isEmpty()) {
            final String replaced = methodArgsReference.replace("/", ".")
                    .replace(";L", ",")
                    .replace(";", ",");
            final List<String> individualArguments = List.of(replaced.split(","));
            final String zzz = individualArguments.stream().map(arg -> {
                return arg + ".*";
            }).collect(Collectors.joining(","));
            pattern = Pattern.compile(methodNameReference + "\\(" + zzz + "\\);");
        }
        final Matcher matcher = pattern.matcher(classMetadata.getClassContent());
        if (matcher.find()) {
            final String sub = classMetadata.getClassContent().substring(matcher.start());
            final Pattern pattern2 = Pattern.compile("(?s)Code:.*?(?=\\n\\n)|(?s)Code:.*?(?=\\n})");
            final Matcher matcher2 = pattern2.matcher(sub);
            if (matcher2.find()) {
                final Pattern pattern3 = Pattern.compile("return\\s", Pattern.MULTILINE);
                final String found = matcher2.group();
                final Matcher matcher3 = pattern3.matcher(found);
                if (matcher3.find()) {
                    final String methodBlock = found.substring(0, matcher3.end());
                    final List<String> lines = List.of(methodBlock.split("\n"));
                    final List<String> filtered = lines.stream()
                            .filter(line -> line.contains("invokevirtual") || line.contains("invokeinterface") || line.contains("invokestatic"))
                            .map(line -> line.replaceAll("^.*invokevirtual.*Method", ""))
                            .map(line -> line.replaceAll("^.*invokeinterface.*Method", ""))
                            .map(line -> line.replaceAll("^.*invokestatic.*Method", ""))
                            .collect(Collectors.toList());
                    return filtered.stream().map(line -> {
                        final String[] parts = line.split(":\\(");
                        final String methodReference = parts[0];
                        final String right = parts[1];
                        final String[] argumentsParts = right.split(";\\)");
                        String arguments = "";
                        if (argumentsParts.length != 1) {
                            arguments = argumentsParts[0].substring(1);
                        }
                        final String[] subParts = methodReference.split("\\.");
                        MethodInvocation methodInvocation;
                        if (subParts.length == 1) {
                            methodInvocation = new MethodInvocation(classMetadata.getClassName(), subParts[0], arguments);
                        } else {
                            final String callObjectClassName = subParts[0].replaceAll("/", ".");
                            methodInvocation = new MethodInvocation(callObjectClassName, subParts[1], arguments);
                        }
                        methodInvocation.setMethodInvocations(new ArrayList<>());
                        final Optional<ClassMetadata> invocationClassMetadata = classMetadataList.stream()
                                .filter(metadata -> metadata.getClassName().equals(methodInvocation.getType()))
                                .findFirst();
                        if (invocationClassMetadata.isPresent()) {
                            final ClassMetadata maybeInterfaceClassMetadata = invocationClassMetadata.get();
                            if (maybeInterfaceClassMetadata.isInterface()) {
                                final List<ClassMetadata> implementations = classMetadataList.stream().filter(metadata -> {
                                    return metadata.implementationOf().stream().anyMatch(impl -> {
                                        return impl.contains(maybeInterfaceClassMetadata.getClassName());
                                    });
                                }).collect(Collectors.toList());
                                final List<MethodInvocation> aggregateInvocations = implementations.stream().map(impl -> {
                                    return findInnerCallsInMethod(impl, classMetadataList, methodInvocation.getMethod(), methodInvocation.getArguments());
                                }).flatMap(List::stream).collect(Collectors.toList());
                                methodInvocation.setMethodInvocations(aggregateInvocations);
                            } else {
                                final List<MethodInvocation> tmp = findInnerCallsInMethod(maybeInterfaceClassMetadata, classMetadataList, methodInvocation.getMethod(),
                                        methodInvocation.getArguments());
                                methodInvocation.setMethodInvocations(tmp);
                            }
                        }
                        return methodInvocation;
                    }).collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }

}
