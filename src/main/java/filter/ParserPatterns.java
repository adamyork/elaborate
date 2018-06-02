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
                    .replace("\r", "")
                    .replace(";;", "")
                    .replace("JL", "long,")
                    .replace("[L", "")
                    .replace(";IL", ",int,")
                    .replace(";ZL", ",boolean,")
                    .replace(";ZZZZ", ",boolean,boolean,boolean,boolean")
                    .replace(";ZZZ", ",boolean,boolean,boolean")
                    .replace(";ZZ", ",boolean,boolean")
                    .replace(";Z", ",boolean")
                    .replace(";L", ",")
                    .replace(";", ",");
            final List<String> allArguments = List.of(replaced.split(","));
            final String normalizedArguments = allArguments.stream()
                    .map(arg -> ".*" + arg + ".*")
                    .collect(Collectors.joining(","));
            return Pattern.compile(methodNameReference + "\\(" + normalizedArguments + "\\);");
        }).or(() -> Optional.of(Pattern.compile(methodNameReference + "\\(.*\\);"))).get();
    }

    public static Pattern buildMultiMethodBodyEndLocatorPattern() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return Pattern.compile("Code:(.*?)return\\r\\n\\r\\n", Pattern.DOTALL);
        }
        return Pattern.compile("return$", Pattern.MULTILINE);
    }

    public static Pattern buildSingleMethodBodyEndLocatorPattern() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return Pattern.compile("Code:(.*)return\\r", Pattern.DOTALL);
        }
        return Pattern.compile("return$", Pattern.MULTILINE);
    }
}
