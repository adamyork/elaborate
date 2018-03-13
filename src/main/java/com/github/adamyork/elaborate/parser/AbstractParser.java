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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        final Pattern isInterfacePattern = Pattern.compile("publicinterface.*" + className);
        final Matcher isInterfaceMatcher = isInterfacePattern.matcher(content);
        if (isInterfaceMatcher.find()) {
            isInterface = true;
        }
        final Pattern implementationOfPattern = Pattern.compile("class.*" + className + "implements.*\\{");
        final Matcher implementationOfMatcher = implementationOfPattern.matcher(content);
        final List<String> interfaces = new ArrayList<>();
        if (implementationOfMatcher.find()) {
            final String group = implementationOfMatcher.group();
            final String matches = group.substring(0, group.length() - 1);
            final String[] interfaceStrings = matches.split("implements")[1].split(",");
            interfaces.addAll(List.of(interfaceStrings));
        }
        return new ClassMetadata(className, content, isInterface, interfaces);
    }
}
