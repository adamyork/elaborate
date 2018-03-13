/*
 * Copyright (c) 2018.
 */

package com.github.adamyork.elaborate.parser;

import com.github.adamyork.elaborate.model.ClassMetadata;
import org.apache.commons.io.IOUtils;
import org.jooq.lambda.Unchecked;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class JarParser extends AbstractParser {

    @Override
    public List<ClassMetadata> parse(final File source, final String inputPath) {
        final JarFile jarFile = Unchecked.function(f -> new JarFile(source)).apply(null);
        final Enumeration<JarEntry> entries = jarFile.entries();
        final List<ClassMetadata> classMetadataList = new ArrayList<>();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.getName().contains(".class")) {
                //TODO the forward slash here may cause issues on different file systems
                final String className = entry.getName().replace("/", ".").replace(".class", "");
                final InputStream in = Unchecked.function(f -> jarFile.getInputStream(entry)).apply(null);
                final File tempFile = Unchecked.function(f -> File.createTempFile("tmp", ".class")).apply(null);
                tempFile.deleteOnExit();
                final FileOutputStream out = Unchecked.function(f -> new FileOutputStream(tempFile)).apply(null);
                Unchecked.function(f -> IOUtils.copy(in, out)).apply(null);
                Unchecked.consumer(f -> in.close()).accept(null);
                Unchecked.consumer(f -> out.close()).accept(null);
                final ClassMetadata classMetadata = buildMetadata(tempFile, className);
                classMetadataList.add(classMetadata);
            }
        }
        return classMetadataList;
    }

}
