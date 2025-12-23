package com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl;

import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMMessage;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMRequest;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolDefinition;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderConfig;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ToolCallingSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClaudeProvider.
 */
class ClaudeProviderTest {

    private ClaudeProvider createProvider(String apiKey) {
        return createProvider(apiKey, null);
    }

    private ClaudeProvider createProvider(String apiKey, String model) {
        ProviderConfig.Builder builder = ProviderConfig.builder()
                .providerId("claude");

        if (apiKey != null) {
            builder.apiKey(apiKey);
        }
        if (model != null) {
            builder.defaultModel(model);
        }

        return new ClaudeProvider(builder.build());
    }

    @Test
    void testGetProviderId() {
        ClaudeProvider provider = createProvider("sk-test-key");
        assertEquals("claude", provider.getProviderId());
    }

    @Test
    void testGetDisplayName() {
        ClaudeProvider provider = createProvider("sk-test-key");
        assertEquals("Anthropic Claude", provider.getDisplayName());
    }

    @Test
    void testIsAvailable_withValidApiKey() {
        ClaudeProvider provider = createProvider("sk-test-api-key");
        assertTrue(provider.isAvailable());
    }

    @Test
    void testIsAvailable_withoutApiKey() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .build();
        ClaudeProvider provider = new ClaudeProvider(config);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testIsAvailable_withEmptyApiKey() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("")
                .build();
        ClaudeProvider provider = new ClaudeProvider(config);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testIsAvailable_withDisabledConfig() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-key")
                .enabled(false)
                .build();
        ClaudeProvider provider = new ClaudeProvider(config);
        assertFalse(provider.isAvailable());
    }

    @Test
    void testGetToolCallingSupport() {
        ClaudeProvider provider = createProvider("sk-test-key");
        ToolCallingSupport support = provider.getToolCallingSupport();

        assertNotNull(support);
        // ToolCallingSupport is an enum - NATIVE means full tool support
        assertEquals(ToolCallingSupport.NATIVE, support);
    }

    @Test
    void testValidateConfig_valid() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-api-key")
                .defaultModel("claude-sonnet-4-20250514")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        ValidationResult result = provider.validateConfig(config);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidateConfig_missingApiKey() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .defaultModel("claude-sonnet-4-20250514")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        ValidationResult result = provider.validateConfig(config);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "apiKey".equals(e.getField())));
    }

    @Test
    void testValidateConfig_emptyApiKey() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("")
                .defaultModel("claude-sonnet-4-20250514")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        ValidationResult result = provider.validateConfig(config);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> "apiKey".equals(e.getField())));
    }

    @Test
    void testValidateConfig_invalidApiKeyPrefix() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("invalid-prefix-key")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        ValidationResult result = provider.validateConfig(config);

        assertTrue(result.isValid()); // Still valid, just with warning
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("sk-")));
    }

    @Test
    void testEstimateTokens() {
        ClaudeProvider provider = createProvider("sk-test-key");

        // Simple test text
        String text = "Hello, world! This is a test message.";
        int estimate = provider.estimateTokens(text);

        // Claude typically uses ~4 chars per token
        // This text has about 38 chars, so ~9-10 tokens
        assertTrue(estimate > 5 && estimate < 20);
    }

    @Test
    void testEstimateTokens_emptyText() {
        ClaudeProvider provider = createProvider("sk-test-key");
        assertEquals(0, provider.estimateTokens(""));
        assertEquals(0, provider.estimateTokens(null));
    }

    @Test
    void testEstimateTokens_longText() {
        ClaudeProvider provider = createProvider("sk-test-key");

        // Generate a long text
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("This is a test sentence. ");
        }

        int estimate = provider.estimateTokens(sb.toString());

        // Should be a reasonable estimate
        assertTrue(estimate > 5000);
        assertTrue(estimate < 10000);
    }

    @Test
    void testConstructor_setsModel() {
        ClaudeProvider provider = createProvider("sk-test-key", "claude-opus-4-20250514");

        // Can't directly check model, but we can verify available
        assertTrue(provider.isAvailable());
    }

    @Test
    void testBuildRequest_withToolDefinitions() {
        // This test verifies that tool definitions can be properly built
        List<LLMToolDefinition> tools = Arrays.asList(
                LLMToolDefinition.builder()
                        .name("test_tool")
                        .description("A test tool")
                        .inputSchema(Collections.singletonMap("type", "object"))
                        .build()
        );

        LLMRequest request = LLMRequest.builder()
                .systemPrompt("You are a helpful assistant")
                .messages(Arrays.asList(LLMMessage.user("Hello")))
                .tools(tools)
                .temperature(0.7)
                .maxTokens(1024)
                .build();

        assertNotNull(request);
        assertEquals(1, request.getTools().size());
        assertEquals("test_tool", request.getTools().get(0).getName());
    }

    @Test
    void testDefaultModel() {
        // When configured without a model, should use default
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-api-key")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);

        // Provider should still be available with default model
        assertTrue(provider.isAvailable());
    }

    @Test
    void testBuildRequest_withMessages() {
        List<LLMMessage> messages = Arrays.asList(
                LLMMessage.user("What is 2+2?"),
                LLMMessage.assistant("2+2 equals 4."),
                LLMMessage.user("What about 3+3?")
        );

        LLMRequest request = LLMRequest.builder()
                .systemPrompt("You are a math helper")
                .messages(messages)
                .build();

        assertNotNull(request);
        assertEquals(3, request.getMessages().size());
        assertEquals("You are a math helper", request.getSystemPrompt());
    }

    @Test
    void testProviderWithCustomBaseUrl() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-key")
                .apiBaseUrl("https://custom-api.example.com/v1")
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        assertTrue(provider.isAvailable());
    }

    @Test
    void testProviderWithMaxTokens() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-key")
                .maxTokens(2000)
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        assertTrue(provider.isAvailable());
    }

    @Test
    void testProviderWithTemperature() {
        ProviderConfig config = ProviderConfig.builder()
                .providerId("claude")
                .apiKey("sk-test-key")
                .temperature(0.8)
                .build();

        ClaudeProvider provider = new ClaudeProvider(config);
        assertTrue(provider.isAvailable());
    }
}
