package com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMMessage;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProvider;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMRequest;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMResponse;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolDefinition;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderConfig;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderException;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.StreamingCallback;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ToolCallingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM Provider implementation for Anthropic's Claude API.
 */
public class ClaudeProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeProvider.class);

    private static final String DEFAULT_API_BASE = "https://api.anthropic.com/v1";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeProvider(ProviderConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();

        logger.info("ClaudeProvider initialized (model: {})",
                config.getDefaultModel() != null ? config.getDefaultModel() : DEFAULT_MODEL);
    }

    @Override
    public String getProviderId() {
        return "claude";
    }

    @Override
    public String getDisplayName() {
        return "Anthropic Claude";
    }

    @Override
    public boolean isAvailable() {
        return config.isEnabled() &&
                config.getApiKey() != null &&
                !config.getApiKey().isEmpty();
    }

    @Override
    public LLMResponse chat(LLMRequest request) throws ProviderException {
        if (!isAvailable()) {
            throw new ProviderException("Claude provider is not available - check API key configuration");
        }

        try {
            // Build API request
            String requestBody = buildApiRequest(request);

            // Log request (without sensitive data)
            logger.debug("Sending request to Claude API: {} messages, {} tools",
                    request.getMessages().size(),
                    request.hasTools() ? request.getTools().size() : 0);

            // Create HTTP request
            String apiBase = config.getApiBaseUrl() != null ?
                    config.getApiBaseUrl() : DEFAULT_API_BASE;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            // Handle response
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode != 200) {
                throw handleErrorResponse(statusCode, responseBody);
            }

            // Parse successful response
            LLMResponse llmResponse = parseResponse(responseBody);

            logger.debug("Received Claude response: {} tokens, stop_reason={}",
                    llmResponse.getTotalTokens(), llmResponse.getStopReason());

            return llmResponse;

        } catch (IOException e) {
            throw new ProviderException("Failed to communicate with Claude API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Request to Claude API was interrupted", e);
        }
    }

    @Override
    public void chatStreaming(LLMRequest request, StreamingCallback callback) throws ProviderException {
        // Streaming implementation would use Server-Sent Events
        // For now, fall back to non-streaming
        logger.warn("Streaming not yet implemented, falling back to non-streaming");
        try {
            LLMResponse response = chat(request);
            callback.onComplete(response);
        } catch (ProviderException e) {
            callback.onError(e);
        }
    }

    @Override
    public ToolCallingSupport getToolCallingSupport() {
        return ToolCallingSupport.NATIVE;
    }

    @Override
    public ValidationResult validateConfig(ProviderConfig config) {
        ValidationResult.Builder result = ValidationResult.builder();

        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            result.addError("apiKey", "API key is required");
        }

        if (config.getApiKey() != null && !config.getApiKey().startsWith("sk-")) {
            result.addWarning("apiKey", "API key should start with 'sk-'");
        }

        return result.build();
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimate: ~4 characters per token for English text
        // Claude uses a similar tokenizer to GPT
        return text.length() / 4;
    }

    /**
     * Builds the API request body as JSON string.
     */
    private String buildApiRequest(LLMRequest request) throws ProviderException {
        try {
            ObjectNode apiRequest = objectMapper.createObjectNode();

            // Model
            String model = request.getModel() != null ? request.getModel() :
                    (config.getDefaultModel() != null ? config.getDefaultModel() : DEFAULT_MODEL);
            apiRequest.put("model", model);

            // Max tokens
            int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() :
                    (config.getMaxTokens() != null ? config.getMaxTokens() : DEFAULT_MAX_TOKENS);
            apiRequest.put("max_tokens", maxTokens);

            // System prompt
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                apiRequest.put("system", request.getSystemPrompt());
            }

            // Temperature
            Double temperature = request.getTemperature() != null ? request.getTemperature() :
                    config.getTemperature();
            if (temperature != null) {
                apiRequest.put("temperature", temperature);
            }

            // Messages
            ArrayNode messages = apiRequest.putArray("messages");
            for (LLMMessage message : request.getMessages()) {
                messages.add(convertMessage(message));
            }

            // Tools
            if (request.hasTools()) {
                ArrayNode tools = apiRequest.putArray("tools");
                for (LLMToolDefinition tool : request.getTools()) {
                    tools.add(convertTool(tool));
                }
            }

            return objectMapper.writeValueAsString(apiRequest);

        } catch (Exception e) {
            throw new ProviderException("Failed to build Claude API request", e);
        }
    }

    /**
     * Converts an LLMMessage to Claude API format.
     */
    private ObjectNode convertMessage(LLMMessage message) {
        ObjectNode msg = objectMapper.createObjectNode();

        // Map role
        String role;
        if (message.getRole() == LLMMessage.MessageRole.SYSTEM) {
            // System messages are handled separately in Claude API
            role = "user";
        } else if (message.getRole() == LLMMessage.MessageRole.TOOL_RESULT) {
            role = "user";
        } else if (message.getRole() == LLMMessage.MessageRole.ASSISTANT) {
            role = "assistant";
        } else {
            role = "user";
        }
        msg.put("role", role);

        // Handle content based on message type
        if (message.getRole() == LLMMessage.MessageRole.TOOL_RESULT) {
            // Tool results are sent as tool_result content blocks
            ArrayNode content = msg.putArray("content");
            ObjectNode toolResult = content.addObject();
            toolResult.put("type", "tool_result");
            toolResult.put("tool_use_id", message.getToolCallId());
            toolResult.put("content", message.getContent());

        } else if (message.hasToolCalls()) {
            // Assistant messages with tool calls
            ArrayNode content = msg.putArray("content");

            // Add text content if present
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                ObjectNode textBlock = content.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", message.getContent());
            }

            // Add tool use blocks
            for (LLMToolCall toolCall : message.getToolCalls()) {
                ObjectNode toolUse = content.addObject();
                toolUse.put("type", "tool_use");
                toolUse.put("id", toolCall.getId());
                toolUse.put("name", toolCall.getName());
                try {
                    toolUse.set("input", objectMapper.readTree(toolCall.getArguments()));
                } catch (Exception e) {
                    // If JSON parsing fails, use empty object
                    toolUse.set("input", objectMapper.createObjectNode());
                }
            }

        } else {
            // Simple text message
            msg.put("content", message.getContent());
        }

        return msg;
    }

    /**
     * Converts an LLMToolDefinition to Claude API format.
     */
    private ObjectNode convertTool(LLMToolDefinition tool) {
        ObjectNode toolNode = objectMapper.createObjectNode();
        toolNode.put("name", tool.getName());
        toolNode.put("description", tool.getDescription());

        // Input schema
        if (tool.getInputSchema() != null) {
            toolNode.set("input_schema", objectMapper.valueToTree(tool.getInputSchema()));
        } else {
            // Default empty schema
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            schema.set("properties", objectMapper.createObjectNode());
            toolNode.set("input_schema", schema);
        }

        return toolNode;
    }

    /**
     * Parses the Claude API response.
     */
    private LLMResponse parseResponse(String responseBody) throws ProviderException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            LLMResponse.Builder builder = LLMResponse.builder()
                    .id(root.path("id").asText())
                    .model(root.path("model").asText())
                    .stopReason(root.path("stop_reason").asText());

            // Parse content blocks
            JsonNode content = root.path("content");
            StringBuilder textContent = new StringBuilder();
            List<LLMToolCall> toolCalls = new ArrayList<>();

            if (content.isArray()) {
                for (JsonNode block : content) {
                    String type = block.path("type").asText();

                    if ("text".equals(type)) {
                        textContent.append(block.path("text").asText());
                    } else if ("tool_use".equals(type)) {
                        LLMToolCall toolCall = LLMToolCall.builder()
                                .id(block.path("id").asText())
                                .name(block.path("name").asText())
                                .arguments(block.path("input").toString())
                                .build();
                        toolCalls.add(toolCall);
                    }
                }
            }

            builder.content(textContent.toString());
            builder.toolCalls(toolCalls);

            // Parse usage
            JsonNode usage = root.path("usage");
            builder.inputTokens(usage.path("input_tokens").asInt());
            builder.outputTokens(usage.path("output_tokens").asInt());

            return builder.build();

        } catch (Exception e) {
            throw new ProviderException("Failed to parse Claude API response", e);
        }
    }

    /**
     * Handles error responses from the Claude API.
     */
    private ProviderException handleErrorResponse(int statusCode, String responseBody) {
        String message;
        boolean retryable = false;

        try {
            JsonNode error = objectMapper.readTree(responseBody);
            JsonNode errorNode = error.path("error");
            message = errorNode.path("message").asText("Unknown error");
        } catch (Exception e) {
            message = responseBody;
        }

        if (statusCode == 401) {
            return ProviderException.unauthorized("claude", "Invalid API key: " + message);
        } else if (statusCode == 429) {
            return ProviderException.rateLimited("claude", "Rate limit exceeded: " + message);
        } else if (statusCode >= 500) {
            return ProviderException.serverError("claude", "Server error: " + message);
        } else if (statusCode == 400) {
            return new ProviderException("claude", "Bad request: " + message, statusCode, false);
        } else {
            return new ProviderException("claude",
                    "API error (" + statusCode + "): " + message, statusCode, retryable);
        }
    }
}
