package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.CallObject;
import com.github.adamyork.elaborate.parser.ArchiveParser;
import com.github.adamyork.elaborate.parser.DirParser;
import com.github.adamyork.elaborate.parser.Parser;
import org.jooq.lambda.Unchecked;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class Engine {

    private final String inputPath;
    private final String className;
    private final String methodName;
    private final String outputFilePath;
    private final Map<Boolean, Parser> parserMap;

    public Engine(final String inputPath, final String className, final String methodName, final String outputFilePath) {
        this.inputPath = inputPath;
        this.className = className;
        this.methodName = methodName;
        this.outputFilePath = outputFilePath;
        parserMap = new HashMap<>();
        parserMap.put(true, new ArchiveParser());
        parserMap.put(false, new DirParser());
    }

    public int run() {
        final ArrayList<Integer> exitCodes = new ArrayList<>();
        exitCodes.add(1);
        final File source = new File(inputPath);
        final boolean isArchive = inputPath.contains(".jar") || inputPath.contains(".war");
        final File file = parserMap.get(isArchive).parse(source, inputPath);
        final Optional<ToolProvider> toolsProviderOptional = ToolProvider.findFirst("javap");
        toolsProviderOptional.ifPresent(toolProvider -> {
            final Charset charset = StandardCharsets.UTF_8;
            final ByteArrayOutputStream contentByteArrayStream = new ByteArrayOutputStream();
            final ByteArrayOutputStream errorByteArrayStream = new ByteArrayOutputStream();
            final PrintStream contentsStream = Unchecked.function(t -> new PrintStream(contentByteArrayStream,
                    true, charset.name())).apply(null);
            final PrintStream errorStream = Unchecked.function(t -> new PrintStream(errorByteArrayStream,
                    true, charset.name())).apply(null);
            toolProvider.run(contentsStream, errorStream, "-c", String.valueOf(file));
            final String content = new String(contentByteArrayStream.toByteArray(), charset);
            final String error = new String(errorByteArrayStream.toByteArray(), charset);
            final String whiteSpaceStripped = content.replace(" ", "");
            contentsStream.close();
            errorStream.close();
            final List<CallObject> callObjects = findInnerCallsInMethod(whiteSpaceStripped);
            Unchecked.consumer(o -> contentByteArrayStream.close()).accept(null);
            Unchecked.consumer(o -> errorByteArrayStream.close()).accept(null);
            exitCodes.clear();
            exitCodes.add(0);
        });
        return exitCodes.get(0);
    }

    public List<CallObject> findInnerCallsInMethod(final String content) {
        final Pattern pattern = Pattern.compile(methodName + "\\(.*\\);");
        final Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            final String sub = content.substring(matcher.start());
            final Pattern pattern2 = Pattern.compile("Code:[\\s\\S]*?(?=\\n{2,})|Code:[\\s\\S]*?(?=}+)");
            final Matcher matcher2 = pattern2.matcher(sub);
            if (matcher2.find()) {
                final String found = matcher2.group();
                final List<String> lines = List.of(found.split("\n"));
                final List<String> filtered = lines.stream()
                        .filter(line -> line.contains("invokevirtual"))
                        .map(line -> line.replaceAll("^.*invokevirtual.*\\/\\/Method", ""))
                        .map(line -> line.substring(0, line.indexOf(":")))
                        .collect(Collectors.toList());
                return filtered.stream().map(line -> {
                    final String[] parts = line.split("\\.");
                    return new CallObject(parts[0], parts[1]);
                }).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

}
