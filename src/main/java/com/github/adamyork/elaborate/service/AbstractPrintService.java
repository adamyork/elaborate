package com.github.adamyork.elaborate.service;

import java.util.Optional;

@SuppressWarnings("WeakerAccess")
class AbstractPrintService {

    protected String normalizeObjectCreation(final String className, final String methodName, final String postFix) {
        final String nextCall = className + "::" + methodName + postFix;
        return Optional.of(nextCall.contains("::\"<init>\""))
                .filter(bool -> bool)
                .map(bool -> "new " + nextCall.replace("::\"<init>\"", ""))
                .orElse(nextCall);
    }

}
