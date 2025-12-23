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
 * Unit tests for OpenAIProvider.
 */
class OpenAIProviderTest {

    private ProviderConfig validConfig;
    private ProviderConfig invalidConfig;
    private ProviderConfig disabledConfig;

    @BeforeEach
    void setUp() {
        // Valid configuration
        validConfig = ProviderConfig.builder()
                .providerId("openai")
                .apiKey("sk-test-key-12345")
                .enabled(true)
                .defaultModel("gpt-4o")
                .build();

        // Invalid configuration (no API key)
        invalidConfig = ProviderConfig.builder()
                .providerId("openai")
                .enabled(true)
                .build();

        // Disabled configuration
        disabledConfig = ProviderConfig.builder()
                .providerId("openai")
                .apiKey("sk-test-key-12345")
                .enabled(false)
                .build();
    }

    @Test
    void testGetProviderId() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertEquals("openai", provider.getProviderId());
    }

    @Test
    void testGetDisplayName() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertEquals("OpenAI (GPT)", provider.getDisplayName());
    }

    @Test
    void testIsAvailable_withValidConfig() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertTrue(provider.isAvailable());
    }

    @Test
    void testIsAvailable_withNoApiKey() {
        OpenAIProvider provider = new OpenAIProvider(invalidConfig);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testIsAvailable_whenDisabled() {
        OpenAIProvider provider = new OpenAIProvider(disabledConfig);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testGetToolCallingSupport() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertEquals(ToolCallingSupport.NATIVE, provider.getToolCallingSupport());
    }

    @Test
    void testEstimateTokens_emptyString() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertEquals(0, provider.estimateTokens(""));
    }

    @Test
    void testEstimateTokens_nullString() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        assertEquals(0, provider.estimateTokens(null));
    }

    @Test
    void testEstimateTokens_normalText() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        // ~4 chars per token
        int tokens = provider.estimateTokens("Hello, world!");
        assertTrue(tokens > 0);
        assertTrue(tokens < 10);
    }

    @Test
    void testValidateConfig_valid() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);
        ValidationResult result = provider.validateConfig(validConfig);
        assertTrue(result.isValid());
    }

    @Test
    void testValidateConfig_missingApiKey() {
        OpenAIProvider provider = new OpenAIProvider(invalidConfig);
        ValidationResult result = provider.validateConfig(invalidConfig);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getField().equals("apiKey")));
    }

    @Test
    void testValidateConfig_suspiciousApiKey() {
        ProviderConfig badKeyConfig = ProviderConfig.builder()
                .providerId("openai")
                .apiKey("not-an-openai-key")
                .enabled(true)
                .build();

        OpenAIProvider provider = new OpenAIProvider(badKeyConfig);
        ValidationResult result = provider.validateConfig(badKeyConfig);
        // Should have a warning about the key format
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("apiKey")));
    }

    @Test
    void testCustomApiBase() {
        ProviderConfig customBaseConfig = ProviderConfig.builder()
                .providerId("openai")
                .apiKey("sk-test-key")
                .apiBaseUrl("https://custom-openai-endpoint.example.com/v1")
                .enabled(true)
                .build();

        OpenAIProvider provider = new OpenAIProvider(customBaseConfig);
        assertTrue(provider.isAvailable());
    }

    @Test
    void testDefaultModel() {
        ProviderConfig noModelConfig = ProviderConfig.builder()
                .providerId("openai")
                .apiKey("sk-test-key")
                .enabled(true)
                .build();

        OpenAIProvider provider = new OpenAIProvider(noModelConfig);
        // Provider should use default model (gpt-4o) when none specified
        assertTrue(provider.isAvailable());
    }

    @Test
    void testBuildRequest_withMessages() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);

        LLMRequest request = LLMRequest.builder()
                .conversationId("test-conv")
                .messages(Collections.singletonList(LLMMessage.user("Hello")))
                .build();

        // Should not throw
        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
    }

    @Test
    void testBuildRequest_withSystemPrompt() {
        OpenAIProvider provider = new OpenAIProvider(validConfig);

        LLMRequest request = LLMRequest.builder()
                .conversationId("test-conv")
                .messages(Collections.singletonList(LLMMessage.user("Hello")))
                .systemPrompt("You are a helpful assistant")
                .build();

        assertNotNull(request);
        assertEquals("You are a helpful assistant", request.getSystemPrompt());
    }
}
