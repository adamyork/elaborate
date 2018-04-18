package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;
import com.github.adamyork.elaborate.model.WriterMemo;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.Unchecked;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class TextService extends AbstractPrintService {

    public void write(final String className, final String methodName, final String outputFilePath,
                      final List<MethodInvocation> methodInvocations,
                      final int indentationLevel, final WriterMemo writerMemo) {
        final String output = build(className, methodName, methodInvocations, indentationLevel, writerMemo).getOutput();
        final List<String> lines = new ArrayList<>(List.of(output.split("\n")));
        final Path file = Paths.get(outputFilePath);
        Unchecked.supplier(() -> Files.write(file, lines, Charset.forName("UTF-8"))).get();
    }

    private WriterMemo build(final String className,
                             final String methodName,
                             final List<MethodInvocation> methodInvocations,
                             final int indentationLevel,
                             final WriterMemo writerMemo) {
        final StringBuilder output = new StringBuilder(writerMemo.getOutput());
        final String initialTabs = StringUtils.repeat("\t", indentationLevel);
        output.append(initialTabs).append(normalizeObjectCreation(className, methodName, " calls\n"));
        final int nextIndentationLevel = indentationLevel + 1;
        final String subsequentTabs = StringUtils.repeat("\t", indentationLevel + 1);
        final String resultOutput = methodInvocations.stream().map(methodInvocation -> {
            final StringBuilder nestedStrings = new StringBuilder();
            if (methodInvocation.getMethodInvocations().size() > 0) {
                final WriterMemo nextMemo = new WriterMemo.Builder(nestedStrings.toString()).build();
                final StringBuilder innerOutput = new StringBuilder(build(methodInvocation.getType(),
                        methodInvocation.getMethod(), methodInvocation.getMethodInvocations(),
                        nextIndentationLevel, nextMemo).getOutput());
                nestedStrings.append(innerOutput);
            } else {
                nestedStrings.append(subsequentTabs).append(normalizeObjectCreation(methodInvocation.getType(),
                        methodInvocation.getMethod(), "\n"));
            }
            return nestedStrings.toString();
        }).collect(Collectors.joining());
        return new WriterMemo.Builder(output + resultOutput).build();
    }

}
