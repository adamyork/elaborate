package filter;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Adam York on 4/20/2018.
 * Copyright 2018
 */
public class ParserPredicates {

    public static Predicate<String> invocationIsNotExcluded(final List<String> excludes) {
        return line -> {
            final String normalized = line.replace("/", ".").split(":")[0];
            return excludes.stream().noneMatch(exclude -> {
                final Pattern excludePattern = Pattern.compile(exclude);
                final Matcher excludeMatcher = excludePattern.matcher(normalized);
                return excludeMatcher.find();
            });
        };
    }

    public static Predicate<String> invocationIsSupported() {
        return line -> line.contains("invokevirtual") ||
                line.contains("invokeinterface") ||
                line.contains("invokestatic") ||
                line.contains("invokespecial") ||
                line.contains("invokedynamic");
    }

    public static Predicate<String> invocationIsNotAlsoImplied(final List<String> implicitMethod) {
        return line -> {
            final String[] leftOfSemiColon = line.split(":");
            if (leftOfSemiColon.length == 0) {
                return true;
            }
            final String firstLeftGroup = leftOfSemiColon[0];
            final String[] selfInvocationGroups = firstLeftGroup.split("\\.");
            if (selfInvocationGroups.length == 0 || selfInvocationGroups.length == 1) {
                return true;
            }
            final String selfInvocation = selfInvocationGroups[1];
            return implicitMethod.stream().noneMatch(include -> include.equals(selfInvocation));
        };
    }
}
