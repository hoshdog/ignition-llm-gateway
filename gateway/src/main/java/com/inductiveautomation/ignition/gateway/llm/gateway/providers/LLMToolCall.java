package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a tool/function call requested by the LLM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LLMToolCall {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String id;
    private final String name;
    private final String arguments;

    private LLMToolCall(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    /**
     * Unique ID for this tool call (used for response correlation)
     */
    public String getId() {
        return id;
    }

    /**
     * Name of the tool being called (e.g., "create_tag")
     */
    public String getName() {
        return name;
    }

    /**
     * JSON string of the tool arguments
     */
    public String getArguments() {
        return arguments;
    }

    /**
     * Parse arguments as a JsonNode for processing
     */
    public JsonNode getArgumentsAsJson() throws ParseException {
        try {
            return MAPPER.readTree(arguments);
        } catch (Exception e) {
            throw new ParseException("Failed to parse tool call arguments as JSON", e);
        }
    }

    @Override
    public String toString() {
        return "LLMToolCall{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", arguments='" + (arguments != null && arguments.length() > 100 ?
                        arguments.substring(0, 100) + "..." : arguments) + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String arguments;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public LLMToolCall build() {
            return new LLMToolCall(this);
        }
    }

    /**
     * Exception thrown when parsing tool call arguments fails
     */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
