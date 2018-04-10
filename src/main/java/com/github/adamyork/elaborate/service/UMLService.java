package com.github.adamyork.elaborate.service;

import com.github.adamyork.elaborate.model.MethodInvocation;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.jooq.lambda.Unchecked;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UMLService {

    private final String className;
    private final String methodName;
    private final String outputFilePath;

    public UMLService(final String className, final String methodName, final String outputFilePath) {
        this.className = className;
        this.methodName = methodName;
        this.outputFilePath = outputFilePath;
    }

    public void write(final List<MethodInvocation> methodInvocations) throws IOException {
        final String startString = "@startuml\n";
        final String ltrString = "left to right direction\n";
        final String componentString = "component A0 [";
        final String packageSubstring = className.substring(0, className.lastIndexOf("."));
        final String classNameSubstring = className.substring(className.lastIndexOf(".") + 1);
        final String noteString = "note top of A0 : " + packageSubstring + "\n";
        final String inner = build(methodInvocations, "A0");
        final String endString = "@enduml";
        final String output = startString + ltrString + componentString + classNameSubstring + "\n" + methodName + "]\n" + noteString + inner + endString;
        final SourceStringReader reader = new SourceStringReader(output);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        reader.generateImage(outputStream, new FileFormatOption(FileFormat.SVG));
        final Path file = Paths.get(outputFilePath);
        Unchecked.function(f -> Files.write(file, Collections.singletonList(outputStream.toString()), Charset.forName("UTF-8"))).apply(null);
        outputStream.close();
    }

    private String build(final List<MethodInvocation> methodInvocations, final String id) {
        if (methodInvocations.isEmpty()) {
            return "";
        }
        return IntStream.range(0, methodInvocations.size())
                .mapToObj(index -> buildUmlString(methodInvocations.get(index), id, index))
                .collect(Collectors.joining());
    }

    private String buildUmlString(final MethodInvocation methodInvocation, final String id, final int index) {
        final StringBuilder output = new StringBuilder();
        final String nextId = id + "D" + index;
        final String componentString = "component " + nextId + " [";
        final String classNameString = methodInvocation.getType();
        final String packageSubstring = classNameString.substring(0, classNameString.lastIndexOf("."));
        final String classNameSubstring = classNameString.substring(classNameString.lastIndexOf(".") + 1);
        final String noteString = "note top of " + nextId + " : " + packageSubstring + "\n";
        final String arrow = id + "-->" + nextId + "\n";
        output.append(componentString)
                .append(classNameSubstring).append("\n")
                .append(methodInvocation.getMethod().replace("\"<init>\"", "new"))
                .append("]\n").append(noteString).append(arrow)
                .append(build(methodInvocation.getMethodInvocations(), nextId));
        return output.toString();
    }

}