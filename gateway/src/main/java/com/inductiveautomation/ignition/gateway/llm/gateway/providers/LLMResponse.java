package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

/**
 * Response from an LLM provider.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LLMResponse {

    private final String id;
    private final String model;
    private final String content;
    private final List<LLMToolCall> toolCalls;
    private final String stopReason;
    private final int inputTokens;
    private final int outputTokens;

    private LLMResponse(Builder builder) {
        this.id = builder.id;
        this.model = builder.model;
        this.content = builder.content;
        this.toolCalls = builder.toolCalls != null ?
                Collections.unmodifiableList(builder.toolCalls) : Collections.emptyList();
        this.stopReason = builder.stopReason;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
    }

    /**
     * Unique ID for this response
     */
    public String getId() {
        return id;
    }

    /**
     * Model used for generation
     */
    public String getModel() {
        return model;
    }

    /**
     * Text content of the response
     */
    public String getContent() {
        return content;
    }

    /**
     * Tool calls requested by the LLM
     */
    public List<LLMToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * Reason the generation stopped (e.g., "end_turn", "tool_use", "max_tokens")
     */
    public String getStopReason() {
        return stopReason;
    }

    /**
     * Number of input tokens used
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * Number of output tokens generated
     */
    public int getOutputTokens() {
        return outputTokens;
    }

    /**
     * Total tokens used
     */
    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * Whether the response contains tool calls
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * Whether the response stopped due to tool use
     */
    public boolean isToolUse() {
        return "tool_use".equals(stopReason);
    }

    @Override
    public String toString() {
        return "LLMResponse{" +
                "id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", content='" + (content != null && content.length() > 50 ?
                        content.substring(0, 50) + "..." : content) + '\'' +
                ", toolCalls=" + toolCalls.size() +
                ", stopReason='" + stopReason + '\'' +
                ", tokens=" + getTotalTokens() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String model;
        private String content;
        private List<LLMToolCall> toolCalls;
        private String stopReason;
        private int inputTokens;
        private int outputTokens;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<LLMToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }
}
