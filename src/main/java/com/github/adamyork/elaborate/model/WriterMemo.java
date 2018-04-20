package com.github.adamyork.elaborate.model;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class WriterMemo {

    private final String output;

    private WriterMemo(final Builder builder) {
        this.output = builder.output;
    }

    public String getOutput() {
        return output;
    }

    public static class Builder {

        private final String output;

        public Builder(final String output) {
            this.output = output;
        }

        public WriterMemo build() {
            return new WriterMemo(this);
        }
    }

}
