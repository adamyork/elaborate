
package com.github.adamyork.elaborate.model;

import java.util.List;

/**
 * Created by Adam York on 3/12/2018.
 * Copyright 2018
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ClassMetadata {

    private final String className;
    private final String classContent;
    private final String superClass;
    private final boolean isInterface;
    private final List<String> interfaces;


    public ClassMetadata(final String className, final String classContent, final String superClass, final boolean isInterface, final List<String> interfaces) {
        this.className = className;
        this.classContent = classContent;
        this.superClass = superClass;
        this.isInterface = isInterface;
        this.interfaces = interfaces;
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
}
