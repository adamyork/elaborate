package com.github.adamyork.elaborate.parser;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ArchiveParser implements Parser {

    private final Map<Boolean, Parser> parserMap;

    public ArchiveParser() {
        parserMap = new HashMap<>();
        parserMap.put(true, new JarParser());
        parserMap.put(false, new WarParser());
    }

    @Override
    public File parse(final File source, final String inputPath) {
        final boolean isJar = inputPath.contains(".jar");
        return parserMap.get(isJar).parse(source, inputPath);
    }
}
