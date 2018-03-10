package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.CallObject;
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
public class WriterService {

    private final String className;
    private final String methodName;
    private final String outputFilePath;

    public WriterService(final String className, final String methodName, final String outputFilePath) {
        this.className = className;
        this.methodName = methodName;
        this.outputFilePath = outputFilePath;
    }

    public void write(final List<CallObject> callObjects, final int indentationLevel, final WriterMemo writerMemo) {
        final String output = build(callObjects, indentationLevel, writerMemo).getOutput();
        final List<String> lines = new ArrayList<>(List.of(output.split("\n")));
        Path file = Paths.get(outputFilePath);
        Unchecked.function(f -> Files.write(file, lines, Charset.forName("UTF-8"))).apply(null);
    }

    private WriterMemo build(final List<CallObject> callObjects, final int indentationLevel, final WriterMemo writerMemo) {
        String output = writerMemo.getOutput();
        String tabs = StringUtils.repeat("\t", indentationLevel);
        output += tabs + className + "::" + methodName + " calls\n";
        final int nextIndentationLevel = indentationLevel + 1;
        tabs = StringUtils.repeat("\t", indentationLevel + 1);
        for (final CallObject callObject : callObjects) {
            if (callObject.getCallObjects().size() > 0) {
                final WriterService writerService = new WriterService(callObject.getType(), callObject.getMethod(), outputFilePath);
                final WriterMemo nextMemo = new WriterMemo(output);
                output = writerService.build(callObject.getCallObjects(), nextIndentationLevel, nextMemo).getOutput();
            } else {
                output += tabs + callObject.getType() + "::" + callObject.getMethod() + "\n";
            }
        }
        return new WriterMemo(output);
    }
}
