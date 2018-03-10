/*
 * Copyright (c) 2018.
 */

package com.github.adamyork.elaborate.parser;

import org.apache.commons.io.IOUtils;
import org.jooq.lambda.Unchecked;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;


/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class JarParser implements Parser {

    @Override
    public Optional<File> parse(final File source, final String inputPath, final String className) {
        final JarFile jarFile = Unchecked.function(f -> new JarFile(source)).apply(null);
        final Enumeration<JarEntry> entries = jarFile.entries();
        File file = null;
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            final String entryName = entry.getName();
            final String normalized = entryName.replaceAll("/", ".");
            if (normalized.contains(className)) {
                final ZipEntry zipEntry = jarFile.getEntry(entryName);
                final InputStream in = Unchecked.function(f -> jarFile.getInputStream(zipEntry)).apply(null);
                final File tempFile = Unchecked.function(f -> File.createTempFile("tmp", ".class")).apply(null);
                tempFile.deleteOnExit();
                final FileOutputStream out = Unchecked.function(f -> new FileOutputStream(tempFile)).apply(null);
                Unchecked.function(f -> IOUtils.copy(in, out)).apply(null);
                file = tempFile;
                break;
            }
        }
        return Optional.ofNullable(file);
    }

}
