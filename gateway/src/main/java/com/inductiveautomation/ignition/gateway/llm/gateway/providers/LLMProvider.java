package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;

/**
 * Core interface for LLM provider implementations.
 * Supports multiple backends (Claude, OpenAI, Ollama, etc.).
 */
public interface LLMProvider {

    /**
     * Provider identifier (e.g., "claude", "openai", "ollama")
     */
    String getProviderId();

    /**
     * Display name for UI
     */
    String getDisplayName();

    /**
     * Check if provider is configured and available
     */
    boolean isAvailable();

    /**
     * Send a request and get a response (blocking)
     */
    LLMResponse chat(LLMRequest request) throws ProviderException;

    /**
     * Send a request with streaming callback
     */
    void chatStreaming(LLMRequest request, StreamingCallback callback) throws ProviderException;

    /**
     * Get supported tool/function calling capability
     */
    ToolCallingSupport getToolCallingSupport();

    /**
     * Validate provider configuration
     */
    ValidationResult validateConfig(ProviderConfig config);

    /**
     * Estimate token count for a message (for context management)
     */
    int estimateTokens(String text);
}
