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

    private String input;
    private String entryClass;
    private String entryMethod;
    private String output;
    private List<String> includes;
    private List<String> excludes;
    private List<String> implicitMethods;

    private Config(final Builder builder) {
        this.input = builder.input;
        this.entryClass = builder.entryClass;
        this.entryMethod = builder.entryMethod;
        this.output = builder.output;
        this.includes = builder.includes;
        this.excludes = builder.excludes;
        this.implicitMethods = builder.implicitMethods;
    }

    public String getInput() {
        return input;
    }

    public String getEntryClass() {
        return entryClass;
    }

    public String getEntryMethod() {
        return entryMethod;
    }

    public String getOutput() {
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

    @JsonPOJOBuilder
    public static class Builder {

        private String input;
        private String entryClass;
        private String entryMethod;
        private String output;
        private List<String> includes;
        private List<String> excludes;
        private List<String> implicitMethods;

        public Builder(@JsonProperty("input") final String input,
                       @JsonProperty("entryClass") final String entryClass,
                       @JsonProperty("entryMethod") final String entryMethod,
                       @JsonProperty("output") final String output,
                       @JsonProperty("includes") final List<String> includes,
                       @JsonProperty("excludes") final List<String> excludes,
                       @JsonProperty("implicitMethods") final List<String> implicitMethods) {
            this.input = input;
            this.entryClass = entryClass;
            this.entryMethod = entryMethod;
            this.output = output;
            this.includes = includes;
            this.excludes = excludes;
            this.implicitMethods = implicitMethods;
        }

        public Builder input(final String input) {
            this.input = input;
            return this;
        }

        public Builder entryClass(final String entryClass) {
            this.entryClass = entryClass;
            return this;
        }

        public Builder entryMethod(final String entryMethod) {
            this.entryMethod = entryMethod;
            return this;
        }

        public Builder output(final String output) {
            this.output = output;
            return this;
        }

        public Builder includes(final List<String> includes) {
            this.includes = includes;
            return this;
        }

        public Builder excludes(final List<String> excludes) {
            this.excludes = excludes;
            return this;
        }

        public Builder implicitMethods(final List<String> implicitMethods) {
            this.implicitMethods = implicitMethods;
            return this;
        }

        public Config build() {
            return new Config(this);
        }

    }

}
