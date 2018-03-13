package com.github.adamyork.elaborate.parser;

import com.github.adamyork.elaborate.model.ClassMetadata;

import java.io.File;
import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public interface Parser {

    List<ClassMetadata> parse(final File source, final String inputPath);

}
