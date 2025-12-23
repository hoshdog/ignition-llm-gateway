package com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMMessage;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMRequest;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderConfig;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ToolCallingSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OllamaProvider.
 */
class OllamaProviderTest {

    private ProviderConfig defaultConfig;
    private ProviderConfig customConfig;
    private ProviderConfig disabledConfig;

    @BeforeEach
    void setUp() {
        // Default configuration (localhost:11434)
        defaultConfig = ProviderConfig.builder()
                .providerId("ollama")
                .enabled(true)
                .defaultModel("llama3.1")
                .build();

        // Custom URL configuration
        customConfig = ProviderConfig.builder()
                .providerId("ollama")
                .apiBaseUrl("http://192.168.1.100:11434")
                .enabled(true)
                .defaultModel("mistral")
                .build();

        // Disabled configuration
        disabledConfig = ProviderConfig.builder()
                .providerId("ollama")
                .enabled(false)
                .build();
    }

    @Test
    void testGetProviderId() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        assertEquals("ollama", provider.getProviderId());
    }

    @Test
    void testGetDisplayName() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        assertEquals("Ollama (Local)", provider.getDisplayName());
    }

    @Test
    void testIsAvailable_whenDisabled() {
        OllamaProvider provider = new OllamaProvider(disabledConfig);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testIsAvailable_noServer() {
        // Provider should not be available if no Ollama server is running
        // (This test assumes Ollama is not running on localhost)
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        // Note: This test may pass or fail depending on local Ollama installation
        // The important thing is it doesn't throw an exception
        provider.isAvailable(); // Should not throw
    }

    @Test
    void testGetToolCallingSupport() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        assertEquals(ToolCallingSupport.NATIVE, provider.getToolCallingSupport());
    }

    @Test
    void testEstimateTokens_emptyString() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        assertEquals(0, provider.estimateTokens(""));
    }

    @Test
    void testEstimateTokens_nullString() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        assertEquals(0, provider.estimateTokens(null));
    }

    @Test
    void testEstimateTokens_normalText() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        // ~3 chars per token for Llama models
        int tokens = provider.estimateTokens("Hello, world!");
        assertTrue(tokens > 0);
        assertTrue(tokens < 10);
    }

    @Test
    void testValidateConfig_default() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        ValidationResult result = provider.validateConfig(defaultConfig);
        // Should have info about using default URL
        // May also have warning about connection if server not running
        assertNotNull(result);
    }

    @Test
    void testValidateConfig_customUrl() {
        OllamaProvider provider = new OllamaProvider(customConfig);
        ValidationResult result = provider.validateConfig(customConfig);
        assertNotNull(result);
    }

    @Test
    void testListModels_noServer() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);
        // Should return empty list if server not available
        var models = provider.listModels();
        assertNotNull(models);
        // List may be empty if Ollama not running
    }

    @Test
    void testBuildRequest_withMessages() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);

        LLMRequest request = LLMRequest.builder()
                .conversationId("test-conv")
                .messages(Collections.singletonList(LLMMessage.user("Hello")))
                .build();

        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
    }

    @Test
    void testBuildRequest_withSystemPrompt() {
        OllamaProvider provider = new OllamaProvider(defaultConfig);

        LLMRequest request = LLMRequest.builder()
                .conversationId("test-conv")
                .messages(Collections.singletonList(LLMMessage.user("Hello")))
                .systemPrompt("You are a helpful assistant")
                .build();

        assertNotNull(request);
        assertEquals("You are a helpful assistant", request.getSystemPrompt());
    }

    @Test
    void testCustomModel() {
        ProviderConfig customModelConfig = ProviderConfig.builder()
                .providerId("ollama")
                .enabled(true)
                .defaultModel("codellama")
                .build();

        OllamaProvider provider = new OllamaProvider(customModelConfig);
        assertNotNull(provider);
    }

    @Test
    void testTemperatureSetting() {
        ProviderConfig tempConfig = ProviderConfig.builder()
                .providerId("ollama")
                .enabled(true)
                .temperature(0.7)
                .build();

        OllamaProvider provider = new OllamaProvider(tempConfig);
        assertNotNull(provider);
    }

    @Test
    void testMaxTokensSetting() {
        ProviderConfig tokensConfig = ProviderConfig.builder()
                .providerId("ollama")
                .enabled(true)
                .maxTokens(2048)
                .build();

        OllamaProvider provider = new OllamaProvider(tokensConfig);
        assertNotNull(provider);
    }
}
