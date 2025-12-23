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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Provider implementation for OpenAI's GPT API.
 * Supports GPT-4o, GPT-4, GPT-3.5-turbo and other OpenAI models.
 */
public class OpenAIProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIProvider.class);

    private static final String DEFAULT_API_BASE = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(ProviderConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();

        logger.info("OpenAIProvider initialized (model: {})",
                config.getDefaultModel() != null ? config.getDefaultModel() : DEFAULT_MODEL);
    }

    @Override
    public String getProviderId() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI (GPT)";
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
            throw new ProviderException("OpenAI provider is not available - check API key configuration");
        }

        try {
            String requestBody = buildApiRequest(request, false);

            logger.debug("Sending request to OpenAI API: {} messages, {} tools",
                    request.getMessages().size(),
                    request.hasTools() ? request.getTools().size() : 0);

            String apiBase = getApiBase();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode != 200) {
                throw handleErrorResponse(statusCode, responseBody);
            }

            LLMResponse llmResponse = parseResponse(responseBody);

            logger.debug("Received OpenAI response: {} tokens, finish_reason={}",
                    llmResponse.getTotalTokens(), llmResponse.getStopReason());

            return llmResponse;

        } catch (IOException e) {
            throw new ProviderException("Failed to communicate with OpenAI API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Request to OpenAI API was interrupted", e);
        }
    }

    @Override
    public void chatStreaming(LLMRequest request, StreamingCallback callback) throws ProviderException {
        if (!isAvailable()) {
            callback.onError(new ProviderException("OpenAI provider is not available"));
            return;
        }

        try {
            String requestBody = buildApiRequest(request, true);
            String apiBase = getApiBase();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Use async for streaming
            CompletableFuture<HttpResponse<java.io.InputStream>> future =
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            future.thenAccept(response -> {
                if (response.statusCode() != 200) {
                    try {
                        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        callback.onError(handleErrorResponse(response.statusCode(), errorBody));
                    } catch (IOException e) {
                        callback.onError(new ProviderException("Failed to read error response", e));
                    }
                    return;
                }

                processStreamingResponse(response.body(), callback);
            }).exceptionally(e -> {
                callback.onError(new ProviderException("Streaming request failed", e));
                return null;
            });

        } catch (Exception e) {
            callback.onError(new ProviderException("Failed to start streaming request", e));
        }
    }

    private void processStreamingResponse(java.io.InputStream inputStream, StreamingCallback callback) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder fullContent = new StringBuilder();
            List<LLMToolCall> toolCalls = new ArrayList<>();
            Map<Integer, StringBuilder> toolCallArgs = new HashMap<>();
            Map<Integer, String> toolCallIds = new HashMap<>();
            Map<Integer, String> toolCallNames = new HashMap<>();
            int inputTokens = 0;
            int outputTokens = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                if (!line.startsWith("data: ")) continue;

                String data = line.substring(6).trim();

                if ("[DONE]".equals(data)) {
                    // Build final tool calls
                    for (Integer idx : toolCallIds.keySet()) {
                        toolCalls.add(LLMToolCall.builder()
                                .id(toolCallIds.get(idx))
                                .name(toolCallNames.get(idx))
                                .arguments(toolCallArgs.getOrDefault(idx, new StringBuilder()).toString())
                                .build());
                    }

                    // Notify tool calls
                    for (LLMToolCall toolCall : toolCalls) {
                        callback.onToolCall(toolCall);
                    }

                    callback.onComplete(LLMResponse.builder()
                            .content(fullContent.toString())
                            .toolCalls(toolCalls)
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .build());
                    return;
                }

                try {
                    JsonNode event = objectMapper.readTree(data);
                    JsonNode choices = event.path("choices");
                    if (choices.isEmpty() || !choices.isArray()) continue;

                    JsonNode choice = choices.get(0);
                    JsonNode delta = choice.path("delta");

                    // Content delta
                    String content = delta.path("content").asText(null);
                    if (content != null) {
                        fullContent.append(content);
                        callback.onToken(content);
                    }

                    // Tool call deltas
                    JsonNode tcArray = delta.path("tool_calls");
                    if (!tcArray.isMissingNode() && tcArray.isArray()) {
                        for (JsonNode tc : tcArray) {
                            int index = tc.path("index").asInt();

                            // Tool call ID comes first
                            String tcId = tc.path("id").asText(null);
                            if (tcId != null) {
                                toolCallIds.put(index, tcId);
                            }

                            // Function name
                            JsonNode function = tc.path("function");
                            String tcName = function.path("name").asText(null);
                            if (tcName != null) {
                                toolCallNames.put(index, tcName);
                            }

                            // Arguments come in chunks
                            String args = function.path("arguments").asText(null);
                            if (args != null) {
                                toolCallArgs.computeIfAbsent(index, k -> new StringBuilder())
                                        .append(args);
                            }
                        }
                    }

                    // Usage info (only in final chunks with stream_options)
                    JsonNode usage = event.path("usage");
                    if (!usage.isMissingNode()) {
                        inputTokens = usage.path("prompt_tokens").asInt(0);
                        outputTokens = usage.path("completion_tokens").asInt(0);
                    }

                } catch (Exception e) {
                    logger.warn("Failed to parse streaming event: {}", e.getMessage());
                }
            }

        } catch (IOException e) {
            callback.onError(new ProviderException("Error reading stream", e));
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
        } else if (!config.getApiKey().startsWith("sk-")) {
            result.addWarning("apiKey", "OpenAI API keys typically start with 'sk-'");
        }

        return result.build();
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // GPT tokenizer is roughly 4 characters per token for English
        return text.length() / 4;
    }

    private String getApiBase() {
        return config.getApiBaseUrl() != null ? config.getApiBaseUrl() : DEFAULT_API_BASE;
    }

    private String buildApiRequest(LLMRequest request, boolean stream) throws ProviderException {
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

            // Streaming
            apiRequest.put("stream", stream);
            if (stream) {
                // Include usage in stream for token counting
                ObjectNode streamOptions = apiRequest.putObject("stream_options");
                streamOptions.put("include_usage", true);
            }

            // Temperature
            Double temperature = request.getTemperature() != null ? request.getTemperature() :
                    config.getTemperature();
            if (temperature != null) {
                apiRequest.put("temperature", temperature);
            }

            // Messages
            ArrayNode messages = apiRequest.putArray("messages");

            // Add system message if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode systemMsg = messages.addObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
            }

            // Add conversation messages
            for (LLMMessage message : request.getMessages()) {
                messages.add(convertMessage(message));
            }

            // Tools
            if (request.hasTools()) {
                ArrayNode tools = apiRequest.putArray("tools");
                for (LLMToolDefinition tool : request.getTools()) {
                    tools.add(convertTool(tool));
                }
                apiRequest.put("tool_choice", "auto");
            }

            return objectMapper.writeValueAsString(apiRequest);

        } catch (Exception e) {
            throw new ProviderException("Failed to build OpenAI API request", e);
        }
    }

    private ObjectNode convertMessage(LLMMessage message) {
        ObjectNode msg = objectMapper.createObjectNode();

        switch (message.getRole()) {
            case USER:
                msg.put("role", "user");
                msg.put("content", message.getContent());
                break;

            case ASSISTANT:
                msg.put("role", "assistant");
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    msg.put("content", message.getContent());
                }
                if (message.hasToolCalls()) {
                    ArrayNode toolCalls = msg.putArray("tool_calls");
                    for (LLMToolCall tc : message.getToolCalls()) {
                        ObjectNode tcNode = toolCalls.addObject();
                        tcNode.put("id", tc.getId());
                        tcNode.put("type", "function");
                        ObjectNode function = tcNode.putObject("function");
                        function.put("name", tc.getName());
                        function.put("arguments", tc.getArguments());
                    }
                }
                break;

            case TOOL_RESULT:
                msg.put("role", "tool");
                msg.put("tool_call_id", message.getToolCallId());
                msg.put("content", message.getContent());
                break;

            case SYSTEM:
                msg.put("role", "system");
                msg.put("content", message.getContent());
                break;
        }

        return msg;
    }

    private ObjectNode convertTool(LLMToolDefinition tool) {
        ObjectNode toolNode = objectMapper.createObjectNode();
        toolNode.put("type", "function");

        ObjectNode function = toolNode.putObject("function");
        function.put("name", tool.getName());
        function.put("description", tool.getDescription());

        if (tool.getInputSchema() != null) {
            function.set("parameters", objectMapper.valueToTree(tool.getInputSchema()));
        } else {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            schema.set("properties", objectMapper.createObjectNode());
            function.set("parameters", schema);
        }

        return toolNode;
    }

    private LLMResponse parseResponse(String responseBody) throws ProviderException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || !choices.isArray()) {
                throw new ProviderException("Invalid response: no choices");
            }

            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");

            LLMResponse.Builder builder = LLMResponse.builder()
                    .id(root.path("id").asText())
                    .model(root.path("model").asText())
                    .stopReason(choice.path("finish_reason").asText());

            // Parse content
            builder.content(message.path("content").asText(""));

            // Parse tool calls
            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isMissingNode() && toolCalls.isArray()) {
                List<LLMToolCall> calls = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    calls.add(LLMToolCall.builder()
                            .id(tc.path("id").asText())
                            .name(tc.path("function").path("name").asText())
                            .arguments(tc.path("function").path("arguments").asText())
                            .build());
                }
                builder.toolCalls(calls);
            }

            // Parse usage
            JsonNode usage = root.path("usage");
            builder.inputTokens(usage.path("prompt_tokens").asInt());
            builder.outputTokens(usage.path("completion_tokens").asInt());

            return builder.build();

        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("Failed to parse OpenAI API response", e);
        }
    }

    private ProviderException handleErrorResponse(int statusCode, String responseBody) {
        String message;

        try {
            JsonNode error = objectMapper.readTree(responseBody);
            JsonNode errorNode = error.path("error");
            message = errorNode.path("message").asText("Unknown error");
        } catch (Exception e) {
            message = responseBody;
        }

        if (statusCode == 401) {
            return ProviderException.unauthorized("openai", "Invalid API key: " + message);
        } else if (statusCode == 429) {
            return ProviderException.rateLimited("openai", "Rate limit exceeded: " + message);
        } else if (statusCode >= 500) {
            return ProviderException.serverError("openai", "Server error: " + message);
        } else if (statusCode == 400) {
            return new ProviderException("openai", "Bad request: " + message, statusCode, false);
        } else {
            return new ProviderException("openai",
                    "API error (" + statusCode + "): " + message, statusCode, false);
        }
    }
}
