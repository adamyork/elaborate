package com.github.adamyork.elaborate.parser;

import java.io.File;

public interface Parser {

    File parse(final File source, final String inputPath);

}
