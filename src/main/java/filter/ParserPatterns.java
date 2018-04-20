package filter;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Adam York on 4/20/2018.
 * Copyright 2018
 */
public class ParserPatterns {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static Pattern buildMethodLocatorPattern(final String methodNameReference, final Optional<String> maybeMethodArgs) {
        return maybeMethodArgs.map(methodArgs -> {
            final String replaced = methodArgs.replace("/", ".")
                    .replace(";IL", ",int,")
                    .replace(";L", ",")
                    .replace(";", ",");
            final List<String> allArguments = List.of(replaced.split(","));
            final String normalizedArguments = allArguments.stream()
                    .map(arg -> ".*" + arg + ".*")
                    .collect(Collectors.joining(","));
            return Pattern.compile(methodNameReference + "\\(" + normalizedArguments + "\\);");
        }).or(() -> Optional.of(Pattern.compile(methodNameReference + "\\(.*\\);"))).get();
    }

    public static Pattern buildMethodBodyEndLocatorPattern() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return Pattern.compile("return\\s", Pattern.MULTILINE);
        }
        return Pattern.compile("return$", Pattern.MULTILINE);
    }
}
