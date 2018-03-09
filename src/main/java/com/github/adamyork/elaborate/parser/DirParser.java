package com.github.adamyork.elaborate.parser;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;

public class DirParser implements Parser {

    @Override
    public File parse(final File source, final String inputPath) {
        final String[] extensions = new String[] {"class"};
        final Collection<File> files = FileUtils.listFiles(source, extensions, true);
        return files.stream()
                .filter(file -> file.getName().contains("ApplicationController"))
                .findFirst().get();
    }

}
