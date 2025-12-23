package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a message in an LLM conversation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LLMMessage {

    /**
     * Role of the message sender
     */
    public enum MessageRole {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL_RESULT
    }

    private final MessageRole role;
    private final String content;
    private final List<LLMToolCall> toolCalls;
    private final String toolCallId;
    private final Map<String, Object> metadata;

    private LLMMessage(Builder builder) {
        this.role = builder.role;
        this.content = builder.content;
        this.toolCalls = builder.toolCalls != null ?
                Collections.unmodifiableList(builder.toolCalls) : null;
        this.toolCallId = builder.toolCallId;
        this.metadata = builder.metadata != null ?
                Collections.unmodifiableMap(new HashMap<>(builder.metadata)) :
                Collections.emptyMap();
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    /**
     * Tool calls made by the assistant (only for ASSISTANT messages)
     */
    public List<LLMToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * ID of the tool call this message is responding to (only for TOOL_RESULT messages)
     */
    public String getToolCallId() {
        return toolCallId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory for user messages
     */
    public static LLMMessage user(String content) {
        return builder()
                .role(MessageRole.USER)
                .content(content)
                .build();
    }

    /**
     * Factory for assistant messages
     */
    public static LLMMessage assistant(String content) {
        return builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();
    }

    /**
     * Factory for system messages
     */
    public static LLMMessage system(String content) {
        return builder()
                .role(MessageRole.SYSTEM)
                .content(content)
                .build();
    }

    /**
     * Factory for tool result messages
     */
    public static LLMMessage toolResult(String toolCallId, String content) {
        return builder()
                .role(MessageRole.TOOL_RESULT)
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }

    @Override
    public String toString() {
        return "LLMMessage{" +
                "role=" + role +
                ", content='" + (content != null && content.length() > 50 ?
                        content.substring(0, 50) + "..." : content) + '\'' +
                ", toolCalls=" + (toolCalls != null ? toolCalls.size() : 0) +
                '}';
    }

    public static class Builder {
        private MessageRole role;
        private String content;
        private List<LLMToolCall> toolCalls;
        private String toolCallId;
        private Map<String, Object> metadata;

        public Builder role(MessageRole role) {
            this.role = role;
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

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public LLMMessage build() {
            return new LLMMessage(this);
        }
    }
}
