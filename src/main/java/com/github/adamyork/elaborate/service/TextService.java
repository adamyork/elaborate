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

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class TextService {

    private final String className;
    private final String methodName;
    private final String outputFilePath;

    public TextService(final String className, final String methodName, final String outputFilePath) {
        this.className = className;
        this.methodName = methodName;
        this.outputFilePath = outputFilePath;
    }

    public void write(final List<MethodInvocation> methodInvocations, final int indentationLevel, final WriterMemo writerMemo) {
        final String output = build(methodInvocations, indentationLevel, writerMemo).getOutput();
        final List<String> lines = new ArrayList<>(List.of(output.split("\n")));
        Path file = Paths.get(outputFilePath);
        Unchecked.function(f -> Files.write(file, lines, Charset.forName("UTF-8"))).apply(null);
    }

    private WriterMemo build(final List<MethodInvocation> methodInvocations, final int indentationLevel, final WriterMemo writerMemo) {
        String output = writerMemo.getOutput();
        String tabs = StringUtils.repeat("\t", indentationLevel);
        output += tabs + normalizeObjectCreation(className, methodName, " calls");
        final int nextIndentationLevel = indentationLevel + 1;
        tabs = StringUtils.repeat("\t", indentationLevel + 1);
        for (final MethodInvocation methodInvocation : methodInvocations) {
            if (methodInvocation.getMethodInvocations().size() > 0) {
                final TextService textService = new TextService(methodInvocation.getType(), methodInvocation.getMethod(), outputFilePath);
                final WriterMemo nextMemo = new WriterMemo.Builder(output).build();
                output = textService.build(methodInvocation.getMethodInvocations(), nextIndentationLevel, nextMemo).getOutput();
            } else {
                output += tabs + normalizeObjectCreation(methodInvocation.getType(), methodInvocation.getMethod(), "");
            }
        }
        return new WriterMemo.Builder(output).build();
    }

    private String normalizeObjectCreation(final String className, final String methodName, final String postFix) {
        String nextCall = className + "::" + methodName + postFix + "\n";
        if (nextCall.contains("::\"<init>\"")) {
            nextCall = "new " + nextCall.replace("::\"<init>\"", "");
        }
        return nextCall;
    }
}
