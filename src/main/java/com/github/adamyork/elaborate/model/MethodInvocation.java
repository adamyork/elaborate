package com.github.adamyork.elaborate.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class MethodInvocation {

    private final String type;
    private final String method;
    private final String arguments;
    private final List<MethodInvocation> methodInvocations;

    private MethodInvocation(final Builder builder) {
        this.type = builder.type;
        this.method = builder.method;
        this.arguments = builder.arguments;
        this.methodInvocations = builder.methodInvocations;
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

    public static class Builder {

        private String type;
        private String method;
        private String arguments;
        private List<MethodInvocation> methodInvocations;

        public Builder(final String type,
                       final String method,
                       final String arguments) {
            this.type = type;
            this.method = method;
            this.arguments = arguments;
            this.methodInvocations = new ArrayList<>();
        }

        public Builder(final String type,
                       final String method,
                       final String arguments,
                       final List<MethodInvocation> methodInvocations) {
            this.type = type;
            this.method = method;
            this.arguments = arguments;
            this.methodInvocations = methodInvocations;
        }

        public MethodInvocation build() {
            return new MethodInvocation(this);
        }

    }

}
