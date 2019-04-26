package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.ArgumentListState;
import com.github.adamyork.elaborate.model.ClassMetadata;
import com.github.adamyork.elaborate.model.MaybeJarOrClassMetadata;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple4;
import org.jooq.lambda.tuple.Tuple5;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
        final List<MaybeJarOrClassMetadata> maybeEntriesAndMaybeMetadataList = Stream.generate(jarEntryIterator::next)
                .takeWhile(i -> jarEntryIterator.hasNext())
                .map(entry -> Optional.of(entry.getName().contains(".class"))
                        .filter(bool -> bool)
                        .map(bool -> {
                            final String trimmed = entry.getName().replace("WEB-INF/classes/", "");
                            final String noSlashes = trimmed.replace("/", ".");
                            final int lastIndexOfClass = noSlashes.lastIndexOf(".class");
                            final String className = noSlashes.substring(0, lastIndexOfClass);
                            final File tempFile = createTempFile(jarFile, entry);
                            final ClassMetadata classMetadata = buildMetadata(tempFile, className);
                            return new MaybeJarOrClassMetadata.Builder<>(Optional.ofNullable(classMetadata))
                                    .build();
                        })
                        .orElseGet(() -> Optional.of(entry.getName().contains(".jar"))
                                .filter(bool -> bool)
                                .map(bool -> new MaybeJarOrClassMetadata.Builder<>(Optional.of(entry)))
                                .orElse(new MaybeJarOrClassMetadata.Builder<>(Optional.empty()))
                                .build()))
                .collect(Collectors.toList());
        LOG.info(libraryIncludes.size() + " include sources found");
        return Optional.of(libraryIncludes.size() > 0)
                .filter(bool -> bool)
                .map(bool -> {
                    final List<JarEntry> libraryEntries = maybeEntriesAndMaybeMetadataList.stream()
                            .filter(maybeJarOrClassMetadata -> {
                                //noinspection unchecked
                                return (Boolean) maybeJarOrClassMetadata.getEntryOrMetadata()
                                        .map(JarEntry.class::isInstance)
                                        .orElse(false);
                            })
                            .map(maybeJarOrClassMetadata -> (JarEntry) Unchecked.supplier(() -> maybeJarOrClassMetadata.getEntryOrMetadata()
                                    .orElseThrow(() -> new RuntimeException("Impossible condition reached")))
                                    .get())
                            .collect(Collectors.toList());
                    final List<ClassMetadata> classMetadataList = maybeEntriesAndMaybeMetadataList.stream()
                            .filter(maybeJarOrClassMetadata -> {
                                //noinspection unchecked
                                return (Boolean) maybeJarOrClassMetadata.getEntryOrMetadata()
                                        .map(ClassMetadata.class::isInstance)
                                        .orElse(false);
                            })
                            .map(maybeJarOrClassMetadata -> (ClassMetadata) Unchecked.supplier(() -> maybeJarOrClassMetadata.getEntryOrMetadata()
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
                            })
                            .flatMap(List::stream)
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

    private Function<Tuple4<ToolProvider, PrintStream, PrintStream, File>,
            Tuple4<ToolProvider, PrintStream, PrintStream, File>> runJavaPFull() {
        return objects -> {
            objects.v1.run(objects.v2, objects.v3, "-c", "-p", "-v", "-constants", String.valueOf(objects.v4));
            return Tuple.tuple(objects.v1, objects.v2, objects.v3, objects.v4);
        };
    }

    private Function<Tuple4<ToolProvider, PrintStream, PrintStream, File>,
            Tuple4<ToolProvider, PrintStream, PrintStream, File>> runJavaPCondensed() {
        return objects -> {
            objects.v1.run(objects.v2, objects.v3, "-c", "-p", String.valueOf(objects.v4));
            return Tuple.tuple(objects.v1, objects.v2, objects.v3, objects.v4);
        };
    }

    private Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> handleFileIo(final ToolProvider toolProvider,
                                                                                                                final File file,
                                                                                                                final Function<Tuple4<ToolProvider, PrintStream, PrintStream, File>,
                                                                                                                        Tuple4<ToolProvider, PrintStream, PrintStream, File>> javaPFunction) {
        final Charset charset = StandardCharsets.UTF_8;
        final ByteArrayOutputStream contentByteArrayStream = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorByteArrayStream = new ByteArrayOutputStream();
        final PrintStream contentsStream = Unchecked.function(t -> new PrintStream(contentByteArrayStream,
                true, charset.name())).apply(null);
        final PrintStream errorStream = Unchecked.function(t -> new PrintStream(errorByteArrayStream,
                true, charset.name())).apply(null);
        javaPFunction.apply(Tuple.tuple(toolProvider, contentsStream, errorStream, file));
        final String content = new String(contentByteArrayStream.toByteArray(), charset);
        final String error = new String(errorByteArrayStream.toByteArray(), charset);
        final String trimmed = content.replace(" ", "");
        return Optional.of(error.isEmpty())
                .filter(bool -> bool)
                .map(bool -> Tuple.tuple(contentsStream, errorStream, contentByteArrayStream, errorByteArrayStream, trimmed))
                .orElseThrow(() -> {
                    LOG.error(error);
                    closeStreams(contentsStream, errorStream, contentByteArrayStream, errorByteArrayStream);
                    return new RuntimeException("Error loading file");
                });
    }

    private ClassMetadata buildMetadata(final File file, final String className) {
        final Optional<ToolProvider> maybeToolProvider = ToolProvider.findFirst("javap");
        return maybeToolProvider
                .map(toolProvider -> {
                    final Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> ioTuple =
                            handleFileIo(toolProvider, file, runJavaPCondensed());
                    assert ioTuple != null;
                    closeStreams(ioTuple.v1, ioTuple.v2, ioTuple.v3, ioTuple.v4);
                    final Map<String, String> methodReferences = buildMethodReferences(file, toolProvider);
                    return buildMetadata(ioTuple.v5, className, methodReferences);
                })
                .orElseThrow(() -> new RuntimeException("Cant find javap"));
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

    //TODO refactor
    private Map<String, String> buildMethodReferences(final File file, final ToolProvider toolProvider) {
        final Tuple5<PrintStream, PrintStream, ByteArrayOutputStream, ByteArrayOutputStream, String> ioTuple =
                handleFileIo(toolProvider, file, runJavaPFull());
        final Pattern bootstrapMethodsStartPattern = Pattern.compile("BootstrapMethods:");
        assert ioTuple != null;
        final Matcher bootstrapMethodsStartMatcher = bootstrapMethodsStartPattern.matcher(ioTuple.v5);
        return Optional.of(bootstrapMethodsStartMatcher.find())
                .filter(bool -> bool)
                .map(bool -> {
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
                }).orElseGet(() -> {
                    closeStreams(ioTuple.v1, ioTuple.v2, ioTuple.v3, ioTuple.v4);
                    return new HashMap<>();
                });
    }

    private ClassMetadata buildMetadata(final String content,
                                        final String className,
                                        final Map<String, String> methodReferences) {
        final String normalizedClassName = className.replace("$", "\\$");
        final Pattern isInterfacePattern = Pattern.compile("publicinterface.*" + normalizedClassName);
        final Matcher isInterfaceMatcher = isInterfacePattern.matcher(content);
        final boolean isInterface = isInterfaceMatcher.find();
        final Pattern superClassPattern = Pattern.compile("class.*" + normalizedClassName + "extends.*\\{");
        final Matcher superClassMatcher = superClassPattern.matcher(content);
        final String superClass = Optional.of(superClassMatcher.find())
                .filter(bool -> bool)
                .map(bool -> {
                    final String superClassGroup = superClassMatcher.group();
                    final String superClassMatches = superClassGroup.substring(0, superClassGroup.length() - 1);
                    final String superClassDeclaration = superClassMatches.split("extends")[1];
                    final String superClassDeclarationNormalized = Optional.of(superClassDeclaration.contains("implements"))
                            .filter(superClassImplements -> superClassImplements)
                            .map(superClassImplements -> superClassDeclaration.split("implements")[0])
                            .orElse(superClassDeclaration);
                    return buildStringsList(superClassDeclarationNormalized).get(0);
                })
                .orElse("");
        final Pattern implementationOfPattern = Pattern.compile("class.*" + normalizedClassName + ".*implements.*\\{");
        final Matcher implementationOfMatcher = implementationOfPattern.matcher(content);
        final List<String> interfaces = Optional.of(implementationOfMatcher.find())
                .filter(bool -> bool)
                .map(bool -> {
                    final String group = implementationOfMatcher.group();
                    final String matches = group.substring(0, group.length() - 1);
                    return buildStringsList(matches.split("implements")[1]);
                })
                .orElse(new ArrayList<>());
        return new ClassMetadata.Builder(className, content, superClass, isInterface, interfaces, methodReferences)
                .build();
    }

    private List<String> buildStringsList(final String input) {
        return eatForArgumentList(new ArgumentListState.Builder(input, 0, "", 0, new ArrayList<>())
                .build())
                .getParsed();
    }

    private ArgumentListState eatForArgumentList(final ArgumentListState state) {
        return Optional.of(state.getIndex() == state.getInput().length() - 1)
                .filter(bool -> bool)
                .map(bool -> state)
                .orElseGet(() -> {
                    final String chr = String.valueOf(state.getInput().charAt(state.getIndex()));
                    final int level = Optional.of(chr.equals("<"))
                            .filter(bool -> bool)
                            .map(bool -> state.getLevel() + 1)
                            .orElseGet(() -> Optional.of(chr.equals(">"))
                                    .filter(bool -> bool)
                                    .map(bool -> state.getLevel() - 1)
                                    .orElseGet(state::getLevel));
                    final Tuple2<String, List<String>> parsedAndMemo = Optional.of(level == 0 && chr.equals(","))
                            .filter(bool -> bool)
                            .map(bool -> {
                                final List<String> lastMemoList = new ArrayList<>();
                                lastMemoList.add(state.getMemo());
                                final List<String> parsedList = new ArrayList<>(state.getParsed());
                                return Tuple.tuple("", Stream.concat(lastMemoList.stream(),
                                        parsedList.stream()).collect(Collectors.toList()));
                            }).orElseGet(() -> Tuple.tuple(state.getMemo() + chr, state.getParsed()));
                    final Integer nextIndex = state.getIndex() + 1;
                    final ArgumentListState nextState = new ArgumentListState.Builder(state.getInput(), nextIndex,
                            parsedAndMemo.v1, level, parsedAndMemo.v2)
                            .build();
                    return eatForArgumentList(nextState);
                });
    }

}
