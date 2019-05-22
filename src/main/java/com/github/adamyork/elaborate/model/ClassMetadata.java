package com.github.adamyork.elaborate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Map;

/**
 * Created by Adam York on 3/12/2018.
 * Copyright 2018
 */
@SuppressWarnings("unused")
@JsonDeserialize(builder = ClassMetadata.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassMetadata {

    private final String className;
    private final String classContent;
    private final String superClass;
    private final boolean isInterface;
    private final List<String> interfaces;
    private final Map<String, String> methodReferences;

    private ClassMetadata(final Builder builder) {
        this.className = builder.className;
        this.classContent = builder.classContent;
        this.superClass = builder.superClass;
        this.isInterface = builder.isInterface;
        this.interfaces = builder.interfaces;
        this.methodReferences = builder.methodReferences;
    }

    public String getClassName() {
        return className;
    }

    public String getClassContent() {
        return classContent;
    }

    public String getSuperClass() {
        return superClass;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public Map<String, String> getMethodReferences() {
        return methodReferences;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final String className;
        private final String classContent;
        private final String superClass;
        private final boolean isInterface;
        private final List<String> interfaces;
        private final Map<String, String> methodReferences;

        public Builder(@JsonProperty(value = "className") final String className,
                       @JsonProperty(value = "classContent") final String classContent,
                       @JsonProperty(value = "superClass") final String superClass,
                       @JsonProperty(value = "interface") final boolean isInterface,
                       @JsonProperty(value = "interfaces") final List<String> interfaces,
                       @JsonProperty(value = "methodReferences") final Map<String, String> methodReferences) {
            this.className = className;
            this.classContent = classContent;
            this.superClass = superClass;
            this.isInterface = isInterface;
            this.interfaces = interfaces;
            this.methodReferences = methodReferences;
        }

        public ClassMetadata build() {
            return new ClassMetadata(this);
        }

    }
}
