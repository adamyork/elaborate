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
        }).orElse(Pattern.compile(methodNameReference + "\\(.*\\);"));
    }

    public static Pattern buildSingleMethodBodyEndLocatorPattern() {
        return Optional.of(System.getProperty("os.name").toLowerCase().contains("win"))
                .filter(bool -> bool)
                .map(bool -> Pattern.compile("Code:(.*)return\\r", Pattern.DOTALL))
                .orElse(Pattern.compile("return$", Pattern.MULTILINE));
    }

    public static Pattern buildMethodBodyEndLocatorPattern() {
        return Optional.of(System.getProperty("os.name").toLowerCase().contains("win"))
                .filter(bool -> bool)
                .map(bool -> Pattern.compile("^[\\s\\S]*?(?=\\r\\n\\r)"))
                .orElse(Pattern.compile("^[\\s\\S]*?(?=\\n{2,})"));
    }

}
