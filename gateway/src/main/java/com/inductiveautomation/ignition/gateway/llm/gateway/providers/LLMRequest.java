package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request to send to an LLM provider.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LLMRequest {

    private final String conversationId;
    private final List<LLMMessage> messages;
    private final List<LLMToolDefinition> tools;
    private final String systemPrompt;
    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final Map<String, Object> providerOptions;

    private LLMRequest(Builder builder) {
        this.conversationId = builder.conversationId;
        this.messages = builder.messages != null ?
                Collections.unmodifiableList(builder.messages) : Collections.emptyList();
        this.tools = builder.tools != null ?
                Collections.unmodifiableList(builder.tools) : null;
        this.systemPrompt = builder.systemPrompt;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.providerOptions = builder.providerOptions != null ?
                Collections.unmodifiableMap(new HashMap<>(builder.providerOptions)) :
                Collections.emptyMap();
    }

    public String getConversationId() {
        return conversationId;
    }

    public List<LLMMessage> getMessages() {
        return messages;
    }

    public List<LLMToolDefinition> getTools() {
        return tools;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Override the default model for this request
     */
    public String getModel() {
        return model;
    }

    /**
     * Temperature for response generation (0.0 - 1.0)
     */
    public Double getTemperature() {
        return temperature;
    }

    /**
     * Maximum tokens in the response
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Provider-specific options (pass-through)
     */
    public Map<String, Object> getProviderOptions() {
        return providerOptions;
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String conversationId;
        private List<LLMMessage> messages;
        private List<LLMToolDefinition> tools;
        private String systemPrompt;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Map<String, Object> providerOptions;

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder messages(List<LLMMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<LLMToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder providerOptions(Map<String, Object> providerOptions) {
            this.providerOptions = providerOptions;
            return this;
        }

        public Builder addProviderOption(String key, Object value) {
            if (this.providerOptions == null) {
                this.providerOptions = new HashMap<>();
            }
            this.providerOptions.put(key, value);
            return this;
        }

        public LLMRequest build() {
            return new LLMRequest(this);
        }
    }
}
