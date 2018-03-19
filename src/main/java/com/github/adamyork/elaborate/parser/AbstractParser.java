/*
 * Copyright (c) 2018.
 */

package com.github.adamyork.elaborate.parser;

import com.github.adamyork.elaborate.model.ClassMetadata;
import org.jooq.lambda.Unchecked;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

public abstract class AbstractParser implements Parser {

    @Override
    public List<ClassMetadata> parse(final File source, final String inputPath) {
        return new ArrayList<>();
    }

    ClassMetadata buildMetadata(final File file, final String className) {
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
            toolProvider.run(contentsStream, errorStream, "-c", String.valueOf(file));
            final String content = new String(contentByteArrayStream.toByteArray(), charset);
            //TODO probably should handle error here
            final String error = new String(errorByteArrayStream.toByteArray(), charset);
            final String trimmed = content.replace(" ", "");
            contentsStream.close();
            errorStream.close();
            final ClassMetadata classMetadata = buildMetadata(trimmed, className);
            Unchecked.consumer(o -> contentByteArrayStream.close()).accept(null);
            Unchecked.consumer(o -> errorByteArrayStream.close()).accept(null);
            return classMetadata;
        }
        return null;
    }

    private ClassMetadata buildMetadata(final String content, final String className) {
        boolean isInterface = false;
        final String normalizedClassName = className.replace("$", "\\$");
        final Pattern isInterfacePattern = Pattern.compile("publicinterface.*" + normalizedClassName);
        final Matcher isInterfaceMatcher = isInterfacePattern.matcher(content);
        if (isInterfaceMatcher.find()) {
            isInterface = true;
        }
        final Pattern implementationOfPattern = Pattern.compile("class.*" + normalizedClassName + "implements.*\\{");
        final Matcher implementationOfMatcher = implementationOfPattern.matcher(content);
        final List<String> interfaces = new ArrayList<>();
        if (implementationOfMatcher.find()) {
            final String group = implementationOfMatcher.group();
            final String matches = group.substring(0, group.length() - 1);
            final List<String> interfaceStrings = buildStringsList(matches.split("implements")[1]);
            interfaces.addAll(interfaceStrings);
        }
        final Pattern superClassPattern = Pattern.compile("class.*" + normalizedClassName + "extends.*\\{");
        final Matcher superClassMatcher = superClassPattern.matcher(content);
        String superClass = "";
        if (superClassMatcher.find()) {
            final String superClassGroup = superClassMatcher.group();
            final String superClassMatches = superClassGroup.substring(0, superClassGroup.length() - 1);
            superClass = buildStringsList(superClassMatches.split("extends")[1]).get(0);
        }
        return new ClassMetadata(className, content, superClass, isInterface, interfaces);
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
