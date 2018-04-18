package com.github.adamyork.elaborate.service;

class AbstractPrintService {

    String normalizeObjectCreation(final String className, final String methodName, final String postFix) {
        String nextCall = className + "::" + methodName + postFix;
        if (nextCall.contains("::\"<init>\"")) {
            nextCall = "new " + nextCall.replace("::\"<init>\"", "");
        }
        return nextCall;
    }

}
