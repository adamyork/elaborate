package com.github.adamyork.elaborate.parser;

import com.github.adamyork.elaborate.model.ClassMetadata;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class ArchiveParser extends AbstractParser {

    private final Map<Boolean, Parser> parserMap;

    public ArchiveParser() {
        parserMap = new HashMap<>();
        parserMap.put(true, new JarParser());
        parserMap.put(false, new WarParser());
    }

    @Override
    public List<ClassMetadata> parse(final File source, final String inputPath) {
        final boolean isJar = inputPath.contains(".jar");
        return parserMap.get(isJar).parse(source, inputPath);
    }
}
