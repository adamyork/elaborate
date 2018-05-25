package com.github.adamyork.elaborate.model;

import java.util.List;
import java.util.Map;

/**
 * Created by Adam York on 3/12/2018.
 * Copyright 2018
 */
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

    public static class Builder {

        private final String className;
        private final String classContent;
        private final String superClass;
        private final boolean isInterface;
        private final List<String> interfaces;
        private final Map<String, String> methodReferences;

        public Builder(final String className,
                       final String classContent,
                       final String superClass,
                       final boolean isInterface,
                       final List<String> interfaces,
                       final Map<String, String> methodReferences) {
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
