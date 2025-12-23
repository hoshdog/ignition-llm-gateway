package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Defines a tool/function that can be called by the LLM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LLMToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;

    private LLMToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.inputSchema = builder.inputSchema;
    }

    /**
     * The tool name (e.g., "create_tag", "read_view")
     */
    public String getName() {
        return name;
    }

    /**
     * Human-readable description of what the tool does
     */
    public String getDescription() {
        return description;
    }

    /**
     * JSON Schema for the tool's input parameters
     */
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    @Override
    public String toString() {
        return "LLMToolDefinition{" +
                "name='" + name + '\'' +
                ", description='" + (description != null && description.length() > 50 ?
                        description.substring(0, 50) + "..." : description) + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public LLMToolDefinition build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Tool name is required");
            }
            return new LLMToolDefinition(this);
        }
    }
}
