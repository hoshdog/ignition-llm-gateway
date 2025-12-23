package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

/**
 * Callback interface for streaming LLM responses.
 */
public interface StreamingCallback {

    /**
     * Called when a new token is received
     */
    void onToken(String token);

    /**
     * Called when a tool call is detected in the stream
     */
    void onToolCall(LLMToolCall toolCall);

    /**
     * Called when the stream is complete
     */
    void onComplete(LLMResponse fullResponse);

    /**
     * Called when an error occurs during streaming
     */
    void onError(ProviderException error);
}
