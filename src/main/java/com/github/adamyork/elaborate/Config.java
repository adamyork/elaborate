package com.github.adamyork.elaborate;

import java.util.List;

public class Config {

    private String input;
    private String entryClass;
    private String entryMethod;
    private String output;
    private List<String> includes;
    private List<String> excludes;
    private List<String> implicitMethods;

    public String getInput() {
        return input;
    }

    public void setInput(final String input) {
        this.input = input;
    }

    public String getEntryClass() {
        return entryClass;
    }

    public void setEntryClass(final String entryClass) {
        this.entryClass = entryClass;
    }

    public String getEntryMethod() {
        return entryMethod;
    }

    public void setEntryMethod(final String entryMethod) {
        this.entryMethod = entryMethod;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(final String output) {
        this.output = output;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(final List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(final List<String> excludes) {
        this.excludes = excludes;
    }

    public List<String> getImplicitMethods() {
        return implicitMethods;
    }

    public void setImplicitMethods(final List<String> implicitMethods) {
        this.implicitMethods = implicitMethods;
    }
}
