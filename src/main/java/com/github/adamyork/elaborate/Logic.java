package com.github.adamyork.elaborate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Logic {

    private String entryMethodName;

    public Logic(final String entryMethodName) {
        this.entryMethodName = entryMethodName;
    }


    public List<CallObject> findInnerCallsInMethod(final String content) {
        final Pattern pattern = Pattern.compile(entryMethodName + "\\(.*\\);");
        final Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            final String sub = content.substring(matcher.start());
            final Pattern pattern2 = Pattern.compile("Code:[\\s\\S]*?(?=\\n{2,})|Code:[\\s\\S]*?(?=}+)");
            final Matcher matcher2 = pattern2.matcher(sub);
            if (matcher2.find()) {
                final String found = matcher2.group();
                final List<String> lines = List.of(found.split("\n"));
                final List<String> filtered = lines.stream()
                        .filter(line -> line.contains("invokevirtual"))
                        .map(line -> line.replaceAll("^.*invokevirtual.*\\/\\/Method", ""))
                        .map(line -> line.substring(0, line.indexOf(":")))
                        .collect(Collectors.toList());
                return filtered.stream().map(line -> {
                    final String[] parts = line.split("\\.");
                    return new CallObject(parts[0], parts[1]);
                }).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

}
