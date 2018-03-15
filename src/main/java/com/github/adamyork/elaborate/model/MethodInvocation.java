package com.github.adamyork.elaborate.model;

import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class MethodInvocation {

    private String type;
    private String method;
    private String arguments;
    private List<MethodInvocation> methodInvocations;

    public MethodInvocation(final String type,
                            final String method,
                            final String arguments) {
        this.type = type;
        this.method = method;
        this.arguments = arguments;
    }

    public String getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }

    public String getArguments() {
        return arguments;
    }

    public List<MethodInvocation> getMethodInvocations() {
        return methodInvocations;
    }

    public void setMethodInvocations(final List<MethodInvocation> methodInvocations) {
        this.methodInvocations = methodInvocations;
    }

}
