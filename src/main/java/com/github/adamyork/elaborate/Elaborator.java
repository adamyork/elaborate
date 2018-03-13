package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.CallObject;
import com.github.adamyork.elaborate.model.ClassMetadata;
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

    public List<CallObject> run() {
        final File source = new File(inputPath);
        final boolean isArchive = inputPath.contains(".jar") || inputPath.contains(".war");
        final List<ClassMetadata> classMetadataList = parserMap.get(isArchive).parse(source, inputPath);
        final Optional<ClassMetadata> targetMetadata = classMetadataList.stream()
                .filter(metadata -> metadata.getClassName().equals(className))
                .findFirst();
        if (targetMetadata.isPresent()) {
            return findInnerCallsInMethod(targetMetadata.get(), classMetadataList);
        } else {
            return new ArrayList<>();
        }
    }

    public List<CallObject> findInnerCallsInMethod(final ClassMetadata classMetadata, final List<ClassMetadata> classMetadataList) {
        final Pattern pattern = Pattern.compile(methodName + "\\(.*\\);");
        final Matcher matcher = pattern.matcher(classMetadata.getClassContent());
        if (matcher.find()) {
            final String sub = classMetadata.getClassContent().substring(matcher.start());
            final Pattern pattern2 = Pattern.compile("Code:[\\s\\S]*?(?=\\n{2,})|Code:[\\s\\S]*?(?=}+)");
            final Matcher matcher2 = pattern2.matcher(sub);
            if (matcher2.find()) {
                final String found = matcher2.group();
                final List<String> lines = List.of(found.split("\n"));
                final List<String> filtered = lines.stream()
                        .filter(line -> line.contains("invokevirtual"))
                        .map(line -> line.replaceAll("^.*invokevirtual.*//Method", ""))
                        .map(line -> line.substring(0, line.indexOf(":")))
                        .collect(Collectors.toList());
                return filtered.stream().map(line -> {
                    final String[] parts = line.split("\\.");
                    CallObject callObject;
                    if (parts.length == 1) {
                        //TODO
                        //either the method is directly on the same object or
                        //the method is on the super class
                        callObject = new CallObject(className, parts[0]);
                    } else {
                        final String callObjectClassName = parts[0].replaceAll("/", ".");
                        callObject = new CallObject(callObjectClassName, parts[1]);
                    }
                    final List<CallObject> callObjects = new ArrayList<>();
                    final boolean isInterface = classMetadataList.stream()
                            .filter(ClassMetadata::isInterface)
                            .anyMatch(metadata -> metadata.getClassName().equals(callObject.getType()));
                    if(isInterface){

                    }
                    //callObjects.add(callObject);
//                    if (usages.size() == 0) {
//                        usages.add(callObject);
//                    }
//                    final List<CallObject> callObjects = usages.stream()
//                            .map(usage -> {
//                                final Elaborator elaborator = new Elaborator(inputPath, callObject.getType(), callObject.getMethod());
//                                return elaborator.run();
//                            }).flatMap(List::stream)
//                            .collect(Collectors.toList());
                    callObject.setCallObjects(callObjects);
                    return callObject;
                }).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

}
