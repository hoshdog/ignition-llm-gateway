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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Provider implementation for Ollama local models.
 * Supports llama, mistral, and other models running locally via Ollama.
 */
public class OllamaProvider implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(OllamaProvider.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.1";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 300; // Local models can be slow

    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaProvider(ProviderConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();

        logger.info("OllamaProvider initialized (base URL: {}, model: {})",
                getBaseUrl(),
                config.getDefaultModel() != null ? config.getDefaultModel() : DEFAULT_MODEL);
    }

    @Override
    public String getProviderId() {
        return "ollama";
    }

    @Override
    public String getDisplayName() {
        return "Ollama (Local)";
    }

    @Override
    public boolean isAvailable() {
        if (!config.isEnabled()) {
            return false;
        }

        // Ping the Ollama server
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.debug("Ollama server not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public LLMResponse chat(LLMRequest request) throws ProviderException {
        try {
            String requestBody = buildApiRequest(request, false);

            logger.debug("Sending request to Ollama API: {} messages",
                    request.getMessages().size());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
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

            logger.debug("Received Ollama response: {} eval tokens",
                    llmResponse.getOutputTokens());

            return llmResponse;

        } catch (IOException e) {
            throw new ProviderException("Failed to communicate with Ollama API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Request to Ollama API was interrupted", e);
        }
    }

    @Override
    public void chatStreaming(LLMRequest request, StreamingCallback callback) throws ProviderException {
        try {
            String requestBody = buildApiRequest(request, true);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
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
            int evalCount = 0;
            int promptEvalCount = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                try {
                    JsonNode event = objectMapper.readTree(line);

                    // Check if done
                    if (event.path("done").asBoolean(false)) {
                        evalCount = event.path("eval_count").asInt(0);
                        promptEvalCount = event.path("prompt_eval_count").asInt(0);

                        // Parse tool calls from final response if present
                        JsonNode messageNode = event.path("message");
                        JsonNode tcArray = messageNode.path("tool_calls");
                        if (!tcArray.isMissingNode() && tcArray.isArray()) {
                            for (JsonNode tc : tcArray) {
                                LLMToolCall toolCall = LLMToolCall.builder()
                                        .id(UUID.randomUUID().toString())
                                        .name(tc.path("function").path("name").asText())
                                        .arguments(tc.path("function").path("arguments").toString())
                                        .build();
                                toolCalls.add(toolCall);
                                callback.onToolCall(toolCall);
                            }
                        }

                        callback.onComplete(LLMResponse.builder()
                                .content(fullContent.toString())
                                .toolCalls(toolCalls)
                                .inputTokens(promptEvalCount)
                                .outputTokens(evalCount)
                                .build());
                        return;
                    }

                    // Content chunk
                    String content = event.path("message").path("content").asText("");
                    if (!content.isEmpty()) {
                        fullContent.append(content);
                        callback.onToken(content);
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
        // Ollama supports tools in newer versions (0.1.24+) for some models
        return ToolCallingSupport.NATIVE;
    }

    @Override
    public ValidationResult validateConfig(ProviderConfig config) {
        ValidationResult.Builder result = ValidationResult.builder();

        String baseUrl = config.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            result.addInfo("apiBaseUrl", "Using default: " + DEFAULT_BASE_URL);
        }

        // Try to connect
        if (!isAvailable()) {
            result.addWarning("connection", "Cannot connect to Ollama server. " +
                    "Ensure Ollama is running at " + getBaseUrl());
        }

        return result.build();
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Llama tokenizer is roughly 3-4 characters per token
        return text.length() / 3;
    }

    /**
     * Lists available models from Ollama.
     */
    public List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                List<String> models = new ArrayList<>();
                JsonNode modelsNode = root.path("models");
                if (modelsNode.isArray()) {
                    for (JsonNode model : modelsNode) {
                        models.add(model.path("name").asText());
                    }
                }
                return models;
            }
        } catch (Exception e) {
            logger.warn("Failed to list Ollama models: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private String getBaseUrl() {
        return config.getApiBaseUrl() != null ? config.getApiBaseUrl() : DEFAULT_BASE_URL;
    }

    private String buildApiRequest(LLMRequest request, boolean stream) throws ProviderException {
        try {
            ObjectNode apiRequest = objectMapper.createObjectNode();

            // Model
            String model = request.getModel() != null ? request.getModel() :
                    (config.getDefaultModel() != null ? config.getDefaultModel() : DEFAULT_MODEL);
            apiRequest.put("model", model);

            // Streaming
            apiRequest.put("stream", stream);

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

            // Tools (Ollama supports tools in newer versions)
            if (request.hasTools()) {
                ArrayNode tools = apiRequest.putArray("tools");
                for (LLMToolDefinition tool : request.getTools()) {
                    tools.add(convertTool(tool));
                }
            }

            // Options
            ObjectNode options = apiRequest.putObject("options");
            if (request.getTemperature() != null) {
                options.put("temperature", request.getTemperature());
            } else if (config.getTemperature() != null) {
                options.put("temperature", config.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                options.put("num_predict", request.getMaxTokens());
            } else if (config.getMaxTokens() != null) {
                options.put("num_predict", config.getMaxTokens());
            }

            return objectMapper.writeValueAsString(apiRequest);

        } catch (Exception e) {
            throw new ProviderException("Failed to build Ollama API request", e);
        }
    }

    private ObjectNode convertMessage(LLMMessage message) {
        ObjectNode msg = objectMapper.createObjectNode();

        switch (message.getRole()) {
            case USER:
                msg.put("role", "user");
                msg.put("content", message.getContent() != null ? message.getContent() : "");
                break;

            case ASSISTANT:
                msg.put("role", "assistant");
                msg.put("content", message.getContent() != null ? message.getContent() : "");
                // Note: Ollama doesn't support tool calls in message history the same way
                break;

            case TOOL_RESULT:
                // Ollama doesn't have a tool role, include as user message
                msg.put("role", "user");
                msg.put("content", "Tool result: " + (message.getContent() != null ? message.getContent() : ""));
                break;

            case SYSTEM:
                msg.put("role", "system");
                msg.put("content", message.getContent() != null ? message.getContent() : "");
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
            JsonNode message = root.path("message");

            LLMResponse.Builder builder = LLMResponse.builder()
                    .model(root.path("model").asText())
                    .content(message.path("content").asText(""));

            // Parse tool calls if present
            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isMissingNode() && toolCalls.isArray()) {
                List<LLMToolCall> calls = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    calls.add(LLMToolCall.builder()
                            .id(UUID.randomUUID().toString()) // Ollama doesn't provide IDs
                            .name(tc.path("function").path("name").asText())
                            .arguments(tc.path("function").path("arguments").toString())
                            .build());
                }
                builder.toolCalls(calls);
            }

            // Token counts
            builder.inputTokens(root.path("prompt_eval_count").asInt(0));
            builder.outputTokens(root.path("eval_count").asInt(0));

            return builder.build();

        } catch (Exception e) {
            throw new ProviderException("Failed to parse Ollama API response", e);
        }
    }

    private ProviderException handleErrorResponse(int statusCode, String responseBody) {
        String message = responseBody;

        try {
            JsonNode error = objectMapper.readTree(responseBody);
            if (error.has("error")) {
                message = error.path("error").asText();
            }
        } catch (Exception ignored) {
            // Use raw response body
        }

        if (statusCode == 404) {
            return new ProviderException("ollama",
                    "Model not found or Ollama server not available: " + message,
                    statusCode, false);
        } else if (statusCode >= 500) {
            return ProviderException.serverError("ollama", "Server error: " + message);
        } else {
            return new ProviderException("ollama",
                    "API error (" + statusCode + "): " + message, statusCode, false);
        }
    }
}
