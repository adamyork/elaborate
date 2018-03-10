package com.github.adamyork.elaborate.parser;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class DirParser implements Parser {

    @Override
    public Optional<File> parse(final File source, final String inputPath, final String className) {
        final String[] extensions = new String[]{"class"};
        final String fileName = className.replace(".", File.separator);
        final Collection<File> files = FileUtils.listFiles(source, extensions, true);
        return files.stream()
                .filter(file -> file.getPath().contains(fileName))
                .findFirst();
    }

}
