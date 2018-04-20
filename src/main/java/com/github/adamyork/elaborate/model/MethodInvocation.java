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
    private final boolean matches;
    private final boolean discreet;
    private final List<MethodInvocation> methodInvocations;

    private MethodInvocation(final Builder builder) {
        this.type = builder.type;
        this.method = builder.method;
        this.arguments = builder.arguments;
        this.matches = builder.matches;
        this.discreet = builder.discreet;
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

    public boolean matches() {
        return matches;
    }

    public boolean discreet() {
        return discreet;
    }

    public List<MethodInvocation> getMethodInvocations() {
        return methodInvocations;
    }

    public static class Builder {

        private final String type;
        private final String method;
        private final String arguments;
        private final List<MethodInvocation> methodInvocations;
        private boolean matches;
        private boolean discreet;

        public Builder(final String type,
                       final String method,
                       final String arguments) {
            this.type = type;
            this.method = method;
            this.arguments = arguments;
            this.discreet = true;
            this.methodInvocations = new ArrayList<>();
        }

        public Builder(final String type,
                       final String method,
                       final String arguments,
                       final List<MethodInvocation> methodInvocations) {
            this.type = type;
            this.method = method;
            this.arguments = arguments;
            this.discreet = true;
            this.methodInvocations = methodInvocations;
        }

        public Builder(final String type,
                       final String method,
                       final String arguments,
                       final boolean matches,
                       final boolean discreet,
                       final List<MethodInvocation> methodInvocations) {
            this.type = type;
            this.method = method;
            this.arguments = arguments;
            this.matches = matches;
            this.discreet = discreet;
            this.methodInvocations = methodInvocations;
        }

        public Builder discreet(final boolean discreet) {
            this.discreet = discreet;
            return this;
        }

        public MethodInvocation build() {
            return new MethodInvocation(this);
        }

    }

}
