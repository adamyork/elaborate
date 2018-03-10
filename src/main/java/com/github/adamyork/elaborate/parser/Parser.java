package com.github.adamyork.elaborate.parser;

import java.io.File;
import java.util.Optional;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public interface Parser {

    Optional<File> parse(final File source, final String inputPath, final String className);

}
