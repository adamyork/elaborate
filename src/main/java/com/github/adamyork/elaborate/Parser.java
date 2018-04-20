package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.Unchecked;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
@SuppressWarnings("WeakerAccess")
public class Parser {

    private static final Logger LOG = LogManager.getLogger(Parser.class);

    public List<ClassMetadata> parse(final File source, final List<String> libraryIncludes) {
        LOG.info("processing sources for " + source.getName());
        final JarFile jarFile = Unchecked.function(f -> new JarFile(source)).apply(null);
        final Enumeration<JarEntry> entries = jarFile.entries();
        final List<ClassMetadata> classMetadataList = new ArrayList<>();
        final List<JarEntry> libraryEntries = new ArrayList<>();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.getName().contains(".class")) {
                final String trimmed = entry.getName().replace("WEB-INF/classes/", "");
                final String noSlashes = trimmed.replace("/", ".");
                final int lastIndexOfClass = noSlashes.lastIndexOf(".class");
                final String className = noSlashes.substring(0, lastIndexOfClass);
                final InputStream in = Unchecked.function(f -> jarFile.getInputStream(entry)).apply(null);
                final File tempFile = Unchecked.function(f -> File.createTempFile("tmp", ".class")).apply(null);
                tempFile.deleteOnExit();
                final FileOutputStream out = Unchecked.function(f -> new FileOutputStream(tempFile)).apply(null);
                Unchecked.function(f -> IOUtils.copy(in, out)).apply(null);
                Unchecked.consumer(f -> in.close()).accept(null);
                Unchecked.consumer(f -> out.close()).accept(null);
                final ClassMetadata classMetadata = buildMetadata(tempFile, className);
                classMetadataList.add(classMetadata);
            } else if (entry.getName().contains(".jar")) {
                libraryEntries.add(entry);
            }
        }
        LOG.info(libraryIncludes.size() + " include sources found");
        if (libraryIncludes.size() > 0) {
            final List<JarEntry> filtered = libraryEntries.stream()
                    .filter(entry -> libraryIncludes.stream()
                            .anyMatch(include -> entry.getName().contains(include)))
                    .collect(Collectors.toList());
            final List<ClassMetadata> allLibraryMetadataList = filtered.stream().map(entry -> {
                final InputStream in = Unchecked.function(f -> jarFile.getInputStream(entry)).apply(null);
                final File tempFile = Unchecked.function(f -> File.createTempFile("tmp", ".class")).apply(null);
                tempFile.deleteOnExit();
                final FileOutputStream out = Unchecked.function(f -> new FileOutputStream(tempFile)).apply(null);
                Unchecked.function(f -> IOUtils.copy(in, out)).apply(null);
                Unchecked.consumer(f -> in.close()).accept(null);
                Unchecked.consumer(f -> out.close()).accept(null);
                final Parser parser = new Parser();
                return parser.parse(tempFile, libraryIncludes);
            }).flatMap(List::stream).collect(Collectors.toList());
            classMetadataList.addAll(allLibraryMetadataList);
        }
        LOG.info("processed all sources for " + source.getName());
        return classMetadataList;
    }

    private ClassMetadata buildMetadata(final File file, final String className) {
        final Optional<ToolProvider> toolsProviderOptional = ToolProvider.findFirst("javap");
        if (toolsProviderOptional.isPresent()) {
            final ToolProvider toolProvider = toolsProviderOptional.get();
            final Charset charset = StandardCharsets.UTF_8;
            final ByteArrayOutputStream contentByteArrayStream = new ByteArrayOutputStream();
            final ByteArrayOutputStream errorByteArrayStream = new ByteArrayOutputStream();
            final PrintStream contentsStream = Unchecked.function(t -> new PrintStream(contentByteArrayStream,
                    true, charset.name())).apply(null);
            final PrintStream errorStream = Unchecked.function(t -> new PrintStream(errorByteArrayStream,
                    true, charset.name())).apply(null);
            toolProvider.run(contentsStream, errorStream, "-c", "-p", String.valueOf(file));
            final String content = new String(contentByteArrayStream.toByteArray(), charset);
            final String error = new String(errorByteArrayStream.toByteArray(), charset);
            final String trimmed = content.replace(" ", "");
            if (!error.isEmpty()) {
                LOG.error(error);
                closeStreams(contentsStream, errorStream, contentByteArrayStream, errorByteArrayStream);
                return null;
            }
            final ClassMetadata classMetadata = buildMetadata(trimmed, className);
            closeStreams(contentsStream, errorStream, contentByteArrayStream, errorByteArrayStream);
            return classMetadata;
        }
        return null;
    }

    private void closeStreams(final PrintStream contentsStream,
                              final PrintStream errorStream,
                              final ByteArrayOutputStream contentByteArrayStream,
                              final ByteArrayOutputStream errorByteArrayStream) {
        contentsStream.close();
        errorStream.close();
        Unchecked.consumer(o -> contentByteArrayStream.close()).accept(null);
        Unchecked.consumer(o -> errorByteArrayStream.close()).accept(null);
    }

    private ClassMetadata buildMetadata(final String content, final String className) {
        boolean isInterface = false;
        final String normalizedClassName = className.replace("$", "\\$");
        final Pattern isInterfacePattern = Pattern.compile("publicinterface.*" + normalizedClassName);
        final Matcher isInterfaceMatcher = isInterfacePattern.matcher(content);
        if (isInterfaceMatcher.find()) {
            isInterface = true;
        }
        final Pattern superClassPattern = Pattern.compile("class.*" + normalizedClassName + "extends.*\\{");
        final Matcher superClassMatcher = superClassPattern.matcher(content);
        String superClass = "";
        if (superClassMatcher.find()) {
            final String superClassGroup = superClassMatcher.group();
            final String superClassMatches = superClassGroup.substring(0, superClassGroup.length() - 1);
            String superClassDeclaration = superClassMatches.split("extends")[1];
            if (superClassDeclaration.contains("implements")) {
                superClassDeclaration = superClassDeclaration.split("implements")[0];
            }
            superClass = buildStringsList(superClassDeclaration).get(0);
        }
        final Pattern implementationOfPattern = Pattern.compile("class.*" + normalizedClassName + ".*implements.*\\{");
        final Matcher implementationOfMatcher = implementationOfPattern.matcher(content);
        final List<String> interfaces = new ArrayList<>();
        if (implementationOfMatcher.find()) {
            final String group = implementationOfMatcher.group();
            final String matches = group.substring(0, group.length() - 1);
            final List<String> interfaceStrings = buildStringsList(matches.split("implements")[1]);
            interfaces.addAll(interfaceStrings);
        }

        return new ClassMetadata.Builder(className, content, superClass, isInterface, interfaces).build();
    }

    private List<String> buildStringsList(final String input) {
        final List<String> interfaceStringsList = new ArrayList<>();
        final List<String> chrs = new LinkedList<>(Arrays.asList(input.split("")));
        StringBuilder current = new StringBuilder();
        int opened = 0;
        while (chrs.size() > 0) {
            final String chr = chrs.remove(0);
            if (chr.equals("<")) {
                opened++;
            }
            if (chr.equals(">")) {
                opened--;
            }
            if (opened == 0 && chr.equals(",")) {
                interfaceStringsList.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(chr);
            }
        }
        interfaceStringsList.add(current.toString());
        return interfaceStringsList;
    }

}
