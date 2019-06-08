package com.github.adamyork.elaborate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
@JsonDeserialize(builder = Config.Builder.class)
public class Config {

    private final String input;
    private final List<String> entryClass;
    private final List<String> entryMethod;
    private final List<String> entryMethodArgs;
    private final List<String> output;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> implicitMethods;
    private final List<String> whiteList;

    private Config(final Builder builder) {
        this.input = builder.input;
        this.entryClass = builder.entryClass;
        this.entryMethod = builder.entryMethod;
        this.entryMethodArgs = builder.entryMethodArgs;
        this.output = builder.output;
        this.includes = builder.includes;
        this.excludes = builder.excludes;
        this.implicitMethods = builder.implicitMethods;
        this.whiteList = builder.whiteList;
    }

    public String getInput() {
        return input;
    }

    public List<String> getEntryClass() {
        return entryClass;
    }

    public List<String> getEntryMethod() {
        return entryMethod;
    }

    public List<String> getEntryMethodArgs() {
        return entryMethodArgs;
    }

    public List<String> getOutput() {
        return output;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public List<String> getImplicitMethods() {
        return implicitMethods;
    }

    public List<String> getWhiteList() {
        return whiteList;
    }

    @SuppressWarnings("WeakerAccess")
    @JsonPOJOBuilder
    public static class Builder {

        private final String input;
        private final List<String> entryClass;
        private final List<String> entryMethod;
        private final List<String> entryMethodArgs;
        private final List<String> output;
        private final List<String> includes;
        private final List<String> excludes;
        private final List<String> implicitMethods;
        private final List<String> whiteList;

        public Builder(@JsonProperty("input") final String input,
                       @JsonProperty("entryClass") final List<String> entryClass,
                       @JsonProperty("entryMethod") final List<String> entryMethod,
                       @JsonProperty("entryMethodArgs") final List<String> entryMethodArgs,
                       @JsonProperty("output") final List<String> output,
                       @JsonProperty("includes") final List<String> includes,
                       @JsonProperty("excludes") final List<String> excludes,
                       @JsonProperty("implicitMethods") final List<String> implicitMethods,
                       @JsonProperty("whiteList") final List<String> whiteList) {
            this.input = input;
            this.entryClass = entryClass;
            this.entryMethod = entryMethod;
            this.entryMethodArgs = entryMethodArgs;
            this.output = output;
            this.includes = includes;
            this.excludes = excludes;
            this.implicitMethods = implicitMethods;
            this.whiteList = whiteList;
        }

        public Config build() {
            return new Config(this);
        }

    }

}
