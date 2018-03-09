package com.github.adamyork.elaborate;

import org.apache.commons.io.FileUtils;
import org.jooq.lambda.Unchecked;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;


public class Main {

    public static void main(final String[] args) {
        final File source = new File("C:\\Users\\Drone\\Desktop\\project-jfx");
        final String[] extensions = new String[]{"class"};
        final Collection<File> files = FileUtils.listFiles(source, extensions, true);
        final Logic logic = new Logic("update");
        final Optional<ToolProvider> toolsProviderOptional = ToolProvider.findFirst("javap");
        final File target = files.stream()
                .filter(file -> file.getName().contains("ControlController"))
                .findFirst().get();
        toolsProviderOptional.ifPresent(toolProvider -> {
            final Charset charset = StandardCharsets.UTF_8;
            final ByteArrayOutputStream contentByteArrayStream = new ByteArrayOutputStream();
            final ByteArrayOutputStream errorByteArrayStream = new ByteArrayOutputStream();
            final PrintStream contentsStream = Unchecked.function(t -> new PrintStream(contentByteArrayStream,
                    true, charset.name())).apply(null);
            final PrintStream errorStream = Unchecked.function(t -> new PrintStream(errorByteArrayStream,
                    true, charset.name())).apply(null);
            toolProvider.run(contentsStream, errorStream, "-c", String.valueOf(target));
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
