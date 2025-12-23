package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for an LLM provider.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ProviderConfig {

    private final String providerId;
    private final String apiKey;
    private final String apiBaseUrl;
    private final String defaultModel;
    private final Integer maxTokens;
    private final Double temperature;
    private final Map<String, String> customHeaders;
    private final boolean enabled;
    private final Integer requestsPerMinute;
    private final Integer tokensPerMinute;

    private ProviderConfig(Builder builder) {
        this.providerId = builder.providerId;
        this.apiKey = builder.apiKey;
        this.apiBaseUrl = builder.apiBaseUrl;
        this.defaultModel = builder.defaultModel;
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.customHeaders = builder.customHeaders != null ?
                Collections.unmodifiableMap(new HashMap<>(builder.customHeaders)) :
                Collections.emptyMap();
        this.enabled = builder.enabled;
        this.requestsPerMinute = builder.requestsPerMinute;
        this.tokensPerMinute = builder.tokensPerMinute;
    }

    public String getProviderId() {
        return providerId;
    }

    /**
     * API key (should be encrypted at rest)
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Base URL for API calls (for custom endpoints like Azure, Ollama, etc.)
     */
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public Integer getTokensPerMinute() {
        return tokensPerMinute;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new builder with values from this config.
     */
    public Builder toBuilder() {
        return new Builder()
                .providerId(this.providerId)
                .apiKey(this.apiKey)
                .apiBaseUrl(this.apiBaseUrl)
                .defaultModel(this.defaultModel)
                .maxTokens(this.maxTokens)
                .temperature(this.temperature)
                .customHeaders(this.customHeaders)
                .enabled(this.enabled)
                .requestsPerMinute(this.requestsPerMinute)
                .tokensPerMinute(this.tokensPerMinute);
    }

    public static class Builder {
        private String providerId;
        private String apiKey;
        private String apiBaseUrl;
        private String defaultModel;
        private Integer maxTokens;
        private Double temperature;
        private Map<String, String> customHeaders;
        private boolean enabled = true;
        private Integer requestsPerMinute;
        private Integer tokensPerMinute;

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
            return this;
        }

        public Builder addCustomHeader(String key, String value) {
            if (this.customHeaders == null) {
                this.customHeaders = new HashMap<>();
            }
            this.customHeaders.put(key, value);
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder requestsPerMinute(Integer requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
            return this;
        }

        public Builder tokensPerMinute(Integer tokensPerMinute) {
            this.tokensPerMinute = tokensPerMinute;
            return this;
        }

        public ProviderConfig build() {
            return new ProviderConfig(this);
        }
    }
}
