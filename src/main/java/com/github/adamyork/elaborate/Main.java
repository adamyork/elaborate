package com.github.adamyork.elaborate;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jooq.lambda.Unchecked;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;


public class Main {

    public static void main(final String[] args) throws MalformedURLException, IOException {
        final File source = new File("C:\\Users\\Drone\\Desktop\\project-jfx.jar");
        final boolean isJar = true;
        final String[] extensions = new String[]{"class"};
        final List<File> targetFileList = new ArrayList<>();
        if (isJar) {
            final JarFile jarFile = new JarFile(source);
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String entryName = entry.getName();
                if (entryName.equals("com/github/adamyork/fx5p1d3r/application/ApplicationController.class")) {
                    final ZipEntry zipEntry = jarFile.getEntry(entryName);
                    final InputStream in = jarFile.getInputStream(zipEntry);
                    final File tempFile = File.createTempFile("tmp", ".class");
                    tempFile.deleteOnExit();
                    final FileOutputStream out = new FileOutputStream(tempFile);
                    IOUtils.copy(in, out);
                    System.out.println("asdf");
                    targetFileList.add(tempFile);
                }
            }
        } else {
            final Collection<File> files = FileUtils.listFiles(source, extensions, true);
            final File tempFile = files.stream()
                    .filter(file -> file.getName().contains("ApplicationController"))
                    .findFirst().get();
            targetFileList.add(tempFile);
        }
        final Logic logic = new Logic("initialize");
        final Optional<ToolProvider> toolsProviderOptional = ToolProvider.findFirst("javap");
        toolsProviderOptional.ifPresent(toolProvider -> {
            final Charset charset = StandardCharsets.UTF_8;
            final ByteArrayOutputStream contentByteArrayStream = new ByteArrayOutputStream();
            final ByteArrayOutputStream errorByteArrayStream = new ByteArrayOutputStream();
            final PrintStream contentsStream = Unchecked.function(t -> new PrintStream(contentByteArrayStream,
                    true, charset.name())).apply(null);
            final PrintStream errorStream = Unchecked.function(t -> new PrintStream(errorByteArrayStream,
                    true, charset.name())).apply(null);
            toolProvider.run(contentsStream, errorStream, "-c", String.valueOf(targetFileList.get(0)));
            final String content = new String(contentByteArrayStream.toByteArray(), charset);
            final String error = new String(errorByteArrayStream.toByteArray(), charset);
            final String whiteSpaceStripped = content.replace(" ", "");
            contentsStream.close();
            errorStream.close();
            final List<CallObject> callObjects = logic.findInnerCallsInMethod(whiteSpaceStripped);
            Unchecked.consumer(o -> contentByteArrayStream.close()).accept(null);
            Unchecked.consumer(o -> errorByteArrayStream.close()).accept(null);
        });
        System.out.println("");
    }
}
