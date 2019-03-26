package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.JarEntryOrClassMetadata;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple5;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        final Iterator<JarEntry> jarEntryIterator = entries.asIterator();
        final List<JarEntryOrClassMetadata> maybeEntriesAndMaybeMetadata = Stream.generate(jarEntryIterator::next)
                .takeWhile(i -> jarEntryIterator.hasNext())
                .map(entry -> jarEntryIterator.next())
                .map(entry -> Optional.of(entry.getName().contains(".class"))
                        .filter(bool -> bool)
                        .map(bool -> {
                            final String trimmed = entry.getName().replace("WEB-INF/classes/", "");
                            final String noSlashes = trimmed.replace("/", ".");
                            final int lastIndexOfClass = noSlashes.lastIndexOf(".class");
                            final String className = noSlashes.substring(0, lastIndexOfClass);
                            final File tempFile = createTempFile(jarFile, entry);
                            final ClassMetadata classMetadata = buildMetadata(tempFile, className);
                            return new JarEntryOrClassMetadata.Builder<>(Optional.ofNullable(classMetadata))
                                    .build();
                        }).orElseGet(() -> Optional.of(entry.getName().contains(".jar"))
                                .filter(bool -> bool)
                                .map(bool -> new JarEntryOrClassMetadata.Builder<>(Optional.of(entry)))
                                .orElse(new JarEntryOrClassMetadata.Builder<>(Optional.empty()))
                                .build()))
                .collect(Collectors.toList());
        LOG.info(libraryIncludes.size() + " include sources found");
        return Optional.of(libraryIncludes.size() > 0)
                .filter(bool -> bool)
                .map(bool -> {
                    final List<JarEntry> libraryEntries = maybeEntriesAndMaybeMetadata.stream()
                            .filter(jarEntryOrClassMetadata -> {
                                //noinspection unchecked
                                return (Boolean) jarEntryOrClassMetadata.getEntryOrMetadata()
                                        .map(JarEntry.class::isInstance)
                                        .orElse(false);
                            })
                            .map(jarEntryOrClassMetadata -> (JarEntry) Unchecked.supplier(() -> jarEntryOrClassMetadata.getEntryOrMetadata()
                                    .orElseThrow(() -> new RuntimeException("Impossible condition reached")))
                                    .get())
                            .collect(Collectors.toList());
                    final List<ClassMetadata> classMetadataList = maybeEntriesAndMaybeMetadata.stream()
                            .filter(jarEntryOrClassMetadata -> {
                                //noinspection unchecked
                                return (Boolean) jarEntryOrClassMetadata.getEntryOrMetadata()
                                        .map(ClassMetadata.class::isInstance)
                                        .orElse(false);
                            })
                            .map(jarEntryOrClassMetadata -> (ClassMetadata) Unchecked.supplier(() -> jarEntryOrClassMetadata.getEntryOrMetadata()
                                    .orElseThrow(() -> new RuntimeException("Impossible condition reached")))
                                    .get())
                            .collect(Collectors.toList());
                    final List<JarEntry> filtered = libraryEntries.stream()
                            .filter(entry -> libraryIncludes.stream()
                                    .anyMatch(include -> entry.getName().contains(include)))
                            .collect(Collectors.toList());
                    final List<ClassMetadata> allLibraryMetadataList = filtered.stream()
                            .map(entry -> {
                                final File tempFile = createTempFile(jarFile, entry);
                                final Parser parser = new Parser();
                                return parser.parse(tempFile, libraryIncludes);
                            }).flatMap(List::stream)
                            .collect(Collectors.toList());
                    LOG.info("processed all sources for " + source.getName());
                    return Stream.concat(classMetadataList.stream(), allLibraryMetadataList.stream())
                            .collect(Collectors.toList());
                }).orElseGet(() -> {
                    LOG.info("processed all sources for " + source.getName());
                    return Collections.emptyList();
                });
    }

    private File createTempFile(final JarFile jarFile, final JarEntry entry) {
        final InputStream in = Unchecked.function(f -> jarFile.getInputStream(entry)).apply(null);
        final File tempFile = Unchecked.function(f -> File.createTempFile("tmp", ".class")).apply(null);
        tempFile.deleteOnExit();
        final FileOutputStream out = Unchecked.function(f -> new FileOutputStream(tempFile)).apply(null);
        Unchecked.function(f -> IOUtils.copy(in, out)).apply(null);
        Unchecked.consumer(f -> in.close()).accept(null);
        Unchecked.consumer(f -> out.close()).accept(null);
        return tempFile;
    }

    private Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> handleFileIo(final ToolProvider toolProvider,
                                                                                                                final File file) {
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
        return Tuple.tuple(contentsStream, errorStream, contentByteArrayStream, errorByteArrayStream, trimmed);
    }

    private ClassMetadata buildMetadata(final File file, final String className) {
        final Optional<ToolProvider> toolsProviderOptional = ToolProvider.findFirst("javap");
        if (toolsProviderOptional.isPresent()) {
            final ToolProvider toolProvider = toolsProviderOptional.get();
            final Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> ioTuple =
                    handleFileIo(toolProvider, file);
            assert ioTuple != null;
            closeStreams(ioTuple.v1, ioTuple.v2, ioTuple.v3, ioTuple.v4);
            final Map<String, String> methodReferences = buildMethodReferences(file, toolProvider);
            return buildMetadata(ioTuple.v5, className, methodReferences);
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

    private Map<String, String> buildMethodReferences(final File file, final ToolProvider toolProvider) {
        final Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> ioTuple =
                handleFileIo(toolProvider, file);
        final Pattern bootstrapMethodsStartPattern = Pattern.compile("BootstrapMethods:");
        assert ioTuple != null;
        final Matcher bootstrapMethodsStartMatcher = bootstrapMethodsStartPattern.matcher(ioTuple.v5);
        if (bootstrapMethodsStartMatcher.find()) {
            final int bootstrapMethodsStartIndex = bootstrapMethodsStartMatcher.start();
            final String bootStrapBlock = ioTuple.v5.substring(bootstrapMethodsStartIndex);
            final Pattern referenceBlocksPattern = Pattern.compile("[0-9]+:(.*?)Methodarguments:", Pattern.DOTALL);
            final Matcher referenceBlocksMatcher = referenceBlocksPattern.matcher(bootStrapBlock);
            final Map<String, String> maybeReferenceMap = new HashMap<>();
            int count = 0;
            while (referenceBlocksMatcher.find()) {
                final String linesToNextBlock = bootStrapBlock.substring(referenceBlocksMatcher.end());
                final Pattern argumentsPattern = Pattern.compile("(.*?)[0-9]+:#", Pattern.DOTALL);
                final Matcher argumentsMatcher = argumentsPattern.matcher(linesToNextBlock);
                if (argumentsMatcher.find()) {
                    final String arguments = argumentsMatcher.group();
                    final String[] lines = arguments.split("\n");
                    maybeReferenceMap.put(Integer.toString(count), lines[2]);
                    count++;
                    continue;
                }
                if (!linesToNextBlock.isEmpty()) {
                    final String[] lines = linesToNextBlock.split("\n");
                    maybeReferenceMap.put(Integer.toString(count), lines[2]);
                    count++;
                }
            }
            closeStreams(ioTuple.v1, ioTuple.v2, ioTuple.v3, ioTuple.v4);
            return maybeReferenceMap;
        }
        closeStreams(ioTuple.v1, ioTuple.v2, ioTuple.v3, ioTuple.v4);
        return null;
    }

    private ClassMetadata buildMetadata(final String content,
                                        final String className,
                                        final Map<String, String> methodReferences) {
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

        return new ClassMetadata.Builder(className, content, superClass, isInterface, interfaces, methodReferences).build();
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
