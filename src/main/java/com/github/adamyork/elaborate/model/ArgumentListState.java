package com.github.adamyork.elaborate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

@SuppressWarnings("unused")
@JsonDeserialize(builder = ArgumentListState.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArgumentListState {

    private final String input;
    private final Integer index;
    private final String memo;
    private final Integer level;
    private final List<String> parsed;

    private ArgumentListState(final Builder builder) {
        input = builder.input;
        index = builder.index;
        memo = builder.memo;
        level = builder.level;
        parsed = builder.parsed;
    }

    public String getInput() {
        return input;
    }

    public Integer getIndex() {
        return index;
    }

    public String getMemo() {
        return memo;
    }

    public Integer getLevel() {
        return level;
    }

    public List<String> getParsed() {
        return parsed;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private final String input;
        private final Integer index;
        private final String memo;
        private final Integer level;
        private final List<String> parsed;

        public Builder(@JsonProperty(value = "input") final String input,
                       @JsonProperty(value = "index") final Integer index,
                       @JsonProperty(value = "memo") final String memo,
                       @JsonProperty(value = "level") final Integer level,
                       @JsonProperty(value = "parsed") final List<String> parsed) {
            this.input = input;
            this.index = index;
            this.memo = memo;
            this.level = level;
            this.parsed = parsed;
        }

        public ArgumentListState build() {
            return new ArgumentListState(this);
        }

    }

}
