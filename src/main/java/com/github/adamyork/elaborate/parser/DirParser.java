package com.github.adamyork.elaborate.parser;

import com.github.adamyork.elaborate.model.ClassMetadata;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class DirParser extends AbstractParser {

    @Override
    public List<ClassMetadata> parse(final File source, final String inputPath) {
        final String[] extensions = new String[]{"class"};
        final Collection<File> files = FileUtils.listFiles(source, extensions, true);
        return files.stream()
                .map(file -> {
                    //TODO class name;
                    return buildMetadata(file, "");
                })
                .collect(Collectors.toList());
    }

}
