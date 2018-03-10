package com.github.adamyork.elaborate.parser;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class ArchiveParser implements Parser {

    private final Map<Boolean, Parser> parserMap;

    public ArchiveParser() {
        parserMap = new HashMap<>();
        parserMap.put(true, new JarParser());
        parserMap.put(false, new WarParser());
    }

    @Override
    public Optional<File> parse(final File source, final String inputPath, final String className) {
        final boolean isJar = inputPath.contains(".jar");
        return parserMap.get(isJar).parse(source, inputPath, className);
    }
}
