package com.github.adamyork.elaborate.model;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class MaybeJarOrClassMetadata<T> {

    private final Optional<T> entryOrMetadata;

    private MaybeJarOrClassMetadata(final Builder<T> builder) {
        entryOrMetadata = builder.entryOrMetadata;
    }

    public Optional<T> getEntryOrMetadata() {
        return entryOrMetadata;
    }

    public static class Builder<T> {

        private final Optional<T> entryOrMetadata;

        public Builder(final Optional<T> entryOrMetadata) {
            this.entryOrMetadata = entryOrMetadata;
        }

        public MaybeJarOrClassMetadata build() {
            return new MaybeJarOrClassMetadata<>(this);
        }

    }

}
