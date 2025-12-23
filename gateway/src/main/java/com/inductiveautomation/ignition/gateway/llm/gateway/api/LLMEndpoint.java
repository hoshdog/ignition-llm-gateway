package com.inductiveautomation.ignition.gateway.llm.gateway.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.LLMGatewayContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.LLMGatewayModuleHolder;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.ApiKey;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.ApiKeyManager;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationException;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthenticationService;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthorizationException;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.CorrelationContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.conversation.ConversationManager;
import com.inductiveautomation.ignition.gateway.llm.gateway.conversation.ConversationResponse;
import com.inductiveautomation.ignition.gateway.llm.gateway.conversation.StreamingResponseHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProvider;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProviderFactory;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderConfig;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderException;
import com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit.RateLimiter;
import com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API endpoint for receiving LLM action requests.
 * Provides health check and action execution endpoints with full authentication.
 *
 * <p>The servlet is mounted at /system/llm-gateway/* by GatewayHook.
 * Since Ignition's WebResourceManager.addServlet() only accepts a Class (not an instance),
 * this servlet uses LLMGatewayModuleHolder to access module dependencies.</p>
 */
public class LLMEndpoint extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(LLMEndpoint.class);

    private LLMGatewayContext llmContext;
    private AuthenticationService authService;
    private ConversationManager conversationManager;
    private RateLimiter rateLimiter;
    private ObjectMapper objectMapper;

    /**
     * No-arg constructor required for servlet container instantiation.
     * Dependencies are retrieved from LLMGatewayModuleHolder.
     */
    public LLMEndpoint() {
        // Dependencies will be initialized lazily from the holder
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Constructor for testing with explicit dependencies.
     */
    public LLMEndpoint(LLMGatewayContext llmContext, ConversationManager conversationManager) {
        this.llmContext = llmContext;
        this.authService = llmContext.getAuthenticationService();
        this.conversationManager = conversationManager;
        this.rateLimiter = llmContext.getRateLimiter();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Initializes dependencies from the module holder if not already set.
     * Called at the start of each request handling method.
     */
    private void ensureInitialized() {
        if (llmContext == null) {
            llmContext = LLMGatewayModuleHolder.getContext();
            if (llmContext != null) {
                authService = llmContext.getAuthenticationService();
                rateLimiter = llmContext.getRateLimiter();
                conversationManager = LLMGatewayModuleHolder.getConversationManager();
                logger.debug("LLMEndpoint initialized from module holder");
            }
        }
    }

    /**
     * Checks if the module is properly initialized.
     */
    private boolean isModuleAvailable() {
        ensureInitialized();
        return llmContext != null;
    }

    /**
     * Normalizes the path by stripping prefixes in order:
     * 1. Servlet name prefix (/llm-gateway) - Ignition may include this
     * 2. API version prefix (/api/v1) - for versioned endpoints
     *
     * E.g., pathInfo "/llm-gateway/api/v1/chat" becomes "/chat"
     *
     * @param rawPath The raw pathInfo from the request
     * @return The normalized path (e.g., "/health" or null)
     */
    private String normalizePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }

        String path = rawPath;

        // Step 1: Strip servlet prefix if present
        if (path.startsWith(SERVLET_PREFIX)) {
            path = path.substring(SERVLET_PREFIX.length());
            if (path.isEmpty()) {
                return null;
            }
        }

        // Step 2: Strip API version prefix if present (for /api/v1/* endpoints)
        if (path.startsWith(API_V1_PREFIX)) {
            path = path.substring(API_V1_PREFIX.length());
            if (path.isEmpty()) {
                return "/";
            }
        }

        return path;
    }

    /**
     * Checks if a path is for admin endpoints.
     */
    private boolean isAdminPath(String path) {
        return path != null && path.startsWith(ADMIN_PREFIX);
    }

    /**
     * Note: Servlet registration is now handled by GatewayHook.startup()
     * using gatewayContext.getWebResourceManager().addServlet()
     *
     * The servlet is mounted at /system/llm-gateway/*
     */

    /**
     * Servlet name prefix for path normalization.
     * Ignition may route requests with pathInfo that includes the servlet name.
     */
    private static final String SERVLET_PREFIX = "/llm-gateway";

    /**
     * API version prefix for versioned endpoints.
     */
    private static final String API_V1_PREFIX = "/api/v1";

    /**
     * Admin endpoint prefix.
     */
    private static final String ADMIN_PREFIX = "/admin";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawPath = req.getPathInfo();
        String path = normalizePath(rawPath);

        // Debug logging - remove after debugging
        logger.debug("LLMEndpoint doGet: rawPathInfo='{}', normalizedPath='{}', servletPath='{}', requestUri='{}'",
                rawPath, path, req.getServletPath(), req.getRequestURI());

        // Health check and OpenAPI spec are available even if module is not fully initialized
        if (path == null || path.isEmpty() || path.equals("/") || path.equals("/health")) {
            handleHealthCheck(resp);
            return;
        } else if (path.equals("/openapi.yaml")) {
            serveOpenApiSpec(resp, false);
            return;
        } else if (path.equals("/openapi.json")) {
            serveOpenApiSpec(resp, true);
            return;
        }

        // Other endpoints require full initialization
        if (!isModuleAvailable()) {
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "LLM Gateway module is not initialized", null);
            return;
        }

        // Admin endpoints - require Gateway admin auth
        if (isAdminPath(path)) {
            handleAdminGet(req, resp, path);
            return;
        }

        if (path.equals("/info")) {
            handleAuthenticatedInfo(req, resp);
        } else if (path.equals("/providers")) {
            handleListProviders(resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found", null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // All POST endpoints require full initialization
        if (!isModuleAvailable()) {
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "LLM Gateway module is not initialized", null);
            return;
        }

        String rawPath = req.getPathInfo();
        String path = normalizePath(rawPath);

        // Debug logging - remove after debugging
        logger.debug("LLMEndpoint doPost: rawPathInfo='{}', normalizedPath='{}'", rawPath, path);

        if (path == null || path.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found", null);
            return;
        }

        // Admin endpoints - require Gateway admin auth
        if (isAdminPath(path)) {
            handleAdminPost(req, resp, path);
            return;
        }

        if (path.equals("/actions") || path.equals("/action")) {
            handleActionRequest(req, resp);
        } else if (path.equals("/chat")) {
            handleChatRequest(req, resp);
        } else if (path.equals("/chat/stream")) {
            handleChatStreamRequest(req, resp);
        } else if (path.equals("/chat/end")) {
            handleEndConversation(req, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Endpoint not found", null);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // All DELETE endpoints require full initialization
        if (!isModuleAvailable()) {
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "LLM Gateway module is not initialized", null);
            return;
        }

        String rawPath = req.getPathInfo();
        String path = normalizePath(rawPath);

        logger.debug("LLMEndpoint doDelete: rawPathInfo='{}', normalizedPath='{}'", rawPath, path);

        // Only admin endpoints support DELETE
        if (isAdminPath(path)) {
            handleAdminDelete(req, resp, path);
            return;
        }

        sendError(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed", null);
    }

    /**
     * Handles health check requests (no authentication required).
     * Works even if module is not fully initialized.
     */
    private void handleHealthCheck(HttpServletResponse resp) throws IOException {
        ensureInitialized();

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("module", LLMGatewayConstants.MODULE_NAME);
        health.put("version", LLMGatewayConstants.MODULE_VERSION);

        if (llmContext != null) {
            health.put("status", llmContext.isRunning() ? "healthy" : "unhealthy");
            health.put("environmentMode", llmContext.getEnvironmentMode().getValue());
        } else {
            health.put("status", "initializing");
            health.put("message", "Module is starting up");
        }

        health.put("timestamp", Instant.now().toString());

        sendJson(resp, HttpServletResponse.SC_OK, health);
    }

    /**
     * Handles authenticated info requests (requires valid API key).
     */
    private void handleAuthenticatedInfo(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correlationId = generateCorrelationId();

        try {
            // Authenticate the request
            AuthContext auth = authService.authenticate(req);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("module", LLMGatewayConstants.MODULE_NAME);
            info.put("version", LLMGatewayConstants.MODULE_VERSION);
            info.put("environmentMode", llmContext.getEnvironmentMode().getValue());
            info.put("supportedResourceTypes", llmContext.getActionExecutor().getSupportedResourceTypes());
            info.put("user", auth.getUserId());
            info.put("permissions", auth.getPermissions().stream()
                    .map(p -> p.getCode())
                    .sorted()
                    .toList());
            info.put("timestamp", Instant.now().toString());

            sendJson(resp, HttpServletResponse.SC_OK, info);

        } catch (AuthenticationException e) {
            handleAuthenticationError(resp, e, correlationId, req);
        }
    }

    /**
     * Handles action execution requests with full authentication and authorization.
     */
    private void handleActionRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correlationId = generateCorrelationId();
        String clientAddress = getClientAddress(req);

        try {
            // Set correlation context for this request
            CorrelationContext.setCorrelationId(correlationId);

            // Step 1: Authenticate the request
            AuthContext authContext;
            try {
                authContext = authService.authenticate(req);
            } catch (AuthenticationException e) {
                handleAuthenticationError(resp, e, correlationId, req);
                return;
            }

            logger.debug("[{}] Authenticated request from {} as {}",
                    correlationId, clientAddress, authContext.getUserId());

            // Step 2: Read and validate request body
            String body = req.getReader().lines().collect(Collectors.joining());

            if (body == null || body.trim().isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Request body is required", correlationId);
                return;
            }

            // Check content length
            if (body.length() > LLMGatewayConstants.MAX_PAYLOAD_SIZE_BYTES) {
                sendError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Request body exceeds maximum size", correlationId);
                return;
            }

            // Step 3: Parse the action request
            Action action;
            try {
                action = objectMapper.readValue(body, Action.class);
            } catch (Exception e) {
                logger.warn("[{}] Failed to parse action request: {}", correlationId, e.getMessage());
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid action request format: " + e.getMessage(), correlationId);
                return;
            }

            // Override correlation ID if not set in action
            if (action.getCorrelationId() == null || action.getCorrelationId().isEmpty()) {
                // Create a new action with our correlation ID
                // This is a limitation of the current action model
                logger.debug("[{}] Using generated correlation ID for action", correlationId);
            }

            // Step 4: Log the action request
            llmContext.getAuditLogger().logActionRequest(action, authContext.getUserId(), clientAddress);

            // Step 5: Execute the action (authorization happens inside executor)
            // Authorization errors are returned as FAILURE results, not thrown
            ActionResult result = llmContext.getActionExecutor().execute(action, authContext);

            // Step 6: Determine HTTP status code based on result
            int statusCode = mapResultToHttpStatus(result);

            // Step 7: Send response
            sendJson(resp, statusCode, result);

            logger.info("[{}] Action {} completed with status {} for user {}",
                    correlationId, action.getActionType(), result.getStatus(), authContext.getUserId());

        } catch (Exception e) {
            logger.error("[{}] Unexpected error processing action request: {}",
                    correlationId, e.getMessage(), e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error", correlationId);
        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * Handles chat/conversation requests with LLM interaction.
     */
    private void handleChatRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correlationId = generateCorrelationId();

        try {
            CorrelationContext.setCorrelationId(correlationId);

            // Authenticate
            AuthContext authContext;
            try {
                authContext = authService.authenticate(req);
            } catch (AuthenticationException e) {
                handleAuthenticationError(resp, e, correlationId, req);
                return;
            }

            // Check if conversation manager is available
            if (conversationManager == null) {
                sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Chat feature is not configured. Please configure an LLM provider.", correlationId);
                return;
            }

            // Parse request body
            String body = req.getReader().lines().collect(Collectors.joining());
            if (body == null || body.trim().isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Request body is required", correlationId);
                return;
            }

            // Parse chat request
            ChatRequest chatRequest;
            try {
                chatRequest = objectMapper.readValue(body, ChatRequest.class);
            } catch (Exception e) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid chat request format: " + e.getMessage(), correlationId);
                return;
            }

            // Validate message
            if (chatRequest.getMessage() == null || chatRequest.getMessage().trim().isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Message is required", correlationId);
                return;
            }

            // Get or generate conversation ID
            String conversationId = chatRequest.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = UUID.randomUUID().toString();
            }

            // Process the message
            logger.debug("[{}] Processing chat message for conversation {} from user {}",
                    correlationId, conversationId, authContext.getUserId());

            ConversationResponse response;
            try {
                response = conversationManager.processMessage(conversationId, chatRequest.getMessage(), authContext);
            } catch (ProviderException e) {
                logger.error("[{}] LLM provider error: {}", correlationId, e.getMessage());
                sendError(resp, HttpServletResponse.SC_BAD_GATEWAY,
                        "LLM provider error: " + e.getMessage(), correlationId);
                return;
            }

            // Build response
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.conversationId = conversationId;
            chatResponse.correlationId = correlationId;
            chatResponse.message = response.getMessage();
            chatResponse.actionResults = response.getActionResults();
            chatResponse.timestamp = Instant.now().toString();

            sendJson(resp, HttpServletResponse.SC_OK, chatResponse);

            logger.info("[{}] Chat response sent for conversation {} ({} actions)",
                    correlationId, conversationId,
                    response.getActionResults() != null ? response.getActionResults().size() : 0);

        } catch (Exception e) {
            logger.error("[{}] Unexpected error processing chat request: {}",
                    correlationId, e.getMessage(), e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error", correlationId);
        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * Handles streaming chat requests using Server-Sent Events (SSE).
     */
    private void handleChatStreamRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correlationId = generateCorrelationId();

        // Set up SSE response headers
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Correlation-ID", correlationId);
        resp.setHeader("X-Accel-Buffering", "no"); // Disable proxy buffering

        // Prevent buffering
        resp.flushBuffer();

        PrintWriter writer = resp.getWriter();

        try {
            CorrelationContext.setCorrelationId(correlationId);

            // Authenticate
            AuthContext authContext;
            try {
                authContext = authService.authenticate(req);
            } catch (AuthenticationException e) {
                sendSSEError(writer, "auth_error", e.getMessage());
                sendSSEDone(writer);
                return;
            }

            // Check if conversation manager is available
            if (conversationManager == null) {
                sendSSEError(writer, "config_error",
                        "Chat feature is not configured. Please configure an LLM provider.");
                sendSSEDone(writer);
                return;
            }

            // Parse request body
            String body = req.getReader().lines().collect(Collectors.joining());
            if (body == null || body.trim().isEmpty()) {
                sendSSEError(writer, "invalid_request", "Request body is required");
                sendSSEDone(writer);
                return;
            }

            // Parse chat request
            ChatRequest chatRequest;
            try {
                chatRequest = objectMapper.readValue(body, ChatRequest.class);
            } catch (Exception e) {
                sendSSEError(writer, "invalid_request", "Invalid chat request format: " + e.getMessage());
                sendSSEDone(writer);
                return;
            }

            // Validate message
            if (chatRequest.getMessage() == null || chatRequest.getMessage().trim().isEmpty()) {
                sendSSEError(writer, "invalid_request", "Message is required");
                sendSSEDone(writer);
                return;
            }

            // Get or generate conversation ID
            String conversationId = chatRequest.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = UUID.randomUUID().toString();
            }

            // Send conversation ID immediately
            sendSSEEvent(writer, "conversation_id",
                    objectMapper.writeValueAsString(Map.of("id", conversationId)));

            // Rate limit check
            if (rateLimiter != null) {
                int estimatedTokens = chatRequest.getMessage().length() / 4; // Rough estimate
                RateLimitResult rateLimitResult = rateLimiter.checkLimit(
                        authContext.getKeyId(), estimatedTokens);

                if (!rateLimitResult.isAllowed()) {
                    sendSSEError(writer, "rate_limit_exceeded", rateLimitResult.getMessage());
                    sendSSEDone(writer);
                    return;
                }
            }

            final String convId = conversationId;
            final String corrId = correlationId;

            logger.debug("[{}] Processing streaming chat for conversation {} from user {}",
                    correlationId, conversationId, authContext.getUserId());

            // Process with streaming
            conversationManager.processMessageStreaming(
                    convId,
                    chatRequest.getMessage(),
                    authContext,
                    new StreamingResponseHandler() {
                        @Override
                        public void onToken(String token) {
                            sendSSEEvent(writer, "token", token);
                        }

                        @Override
                        public void onToolCallStart(LLMToolCall toolCall) {
                            try {
                                sendSSEEvent(writer, "tool_call_start",
                                        objectMapper.writeValueAsString(Map.of(
                                                "id", toolCall.getId(),
                                                "name", toolCall.getName()
                                        )));
                            } catch (Exception e) {
                                logger.warn("Failed to serialize tool call start", e);
                            }
                        }

                        @Override
                        public void onToolCallComplete(LLMToolCall toolCall, ActionResult result) {
                            try {
                                sendSSEEvent(writer, "tool_call_complete",
                                        objectMapper.writeValueAsString(Map.of(
                                                "id", toolCall.getId(),
                                                "status", result.getStatus().name(),
                                                "message", result.getMessage() != null ? result.getMessage() : ""
                                        )));
                            } catch (Exception e) {
                                logger.warn("Failed to serialize tool call complete", e);
                            }
                        }

                        @Override
                        public void onComplete(ConversationResponse response) {
                            try {
                                Map<String, Object> completeData = new LinkedHashMap<>();
                                completeData.put("conversationId", convId);
                                completeData.put("correlationId", corrId);
                                if (response.getActionResults() != null) {
                                    completeData.put("actionCount", response.getActionResults().size());
                                }
                                if (response.getTokensUsed() > 0) {
                                    completeData.put("tokensUsed", response.getTokensUsed());
                                }

                                sendSSEEvent(writer, "complete",
                                        objectMapper.writeValueAsString(completeData));
                            } catch (Exception e) {
                                logger.warn("Failed to serialize complete event", e);
                            }
                            sendSSEDone(writer);
                        }

                        @Override
                        public void onError(Exception error) {
                            sendSSEError(writer, "processing_error", error.getMessage());
                            sendSSEDone(writer);
                        }
                    }
            );

            logger.info("[{}] Streaming chat initiated for conversation {}",
                    correlationId, conversationId);

        } catch (Exception e) {
            logger.error("[{}] Unexpected error in streaming chat: {}",
                    correlationId, e.getMessage(), e);
            sendSSEError(writer, "internal_error", "Internal server error");
            sendSSEDone(writer);
        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * Sends an SSE event to the client.
     */
    private void sendSSEEvent(PrintWriter writer, String eventType, String data) {
        writer.write("event: " + eventType + "\n");
        // Handle multi-line data
        for (String line : data.split("\n")) {
            writer.write("data: " + line + "\n");
        }
        writer.write("\n");
        writer.flush();
    }

    /**
     * Sends an SSE error event.
     */
    private void sendSSEError(PrintWriter writer, String errorType, String message) {
        try {
            String errorData = objectMapper.writeValueAsString(Map.of(
                    "error", errorType,
                    "message", message != null ? message : "Unknown error"
            ));
            sendSSEEvent(writer, "error", errorData);
        } catch (Exception e) {
            writer.write("event: error\ndata: {\"error\":\"unknown\"}\n\n");
            writer.flush();
        }
    }

    /**
     * Sends the SSE done event to signal stream end.
     */
    private void sendSSEDone(PrintWriter writer) {
        writer.write("event: done\ndata: {}\n\n");
        writer.flush();
    }

    /**
     * Serves the OpenAPI specification.
     */
    private void serveOpenApiSpec(HttpServletResponse resp, boolean asJson) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/openapi/llm-gateway-api.yaml")) {
            if (is == null) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "OpenAPI spec not found", null);
                return;
            }

            String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (asJson) {
                // Convert YAML to JSON using a simple approach
                // For a proper implementation, use a YAML library
                // For now, we'll serve a simplified JSON representation
                try {
                    // Parse YAML as nested structure and convert to JSON
                    // This is a simplified approach - a real implementation would use SnakeYAML
                    resp.setContentType("application/json");
                    resp.setCharacterEncoding("UTF-8");

                    // Create a simplified JSON representation
                    Map<String, Object> spec = new LinkedHashMap<>();
                    spec.put("openapi", "3.0.3");
                    spec.put("info", Map.of(
                            "title", "Ignition LLM Gateway API",
                            "version", "1.0.0"
                    ));
                    spec.put("note", "For full specification, use /openapi.yaml");

                    objectMapper.writeValue(resp.getWriter(), spec);
                } catch (Exception e) {
                    logger.error("Failed to convert OpenAPI spec to JSON", e);
                    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "Failed to convert spec to JSON", null);
                }
            } else {
                resp.setContentType("text/yaml");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write(yaml);
            }
        }
    }

    /**
     * Lists available LLM providers and their status.
     */
    private void handleListProviders(HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> providerList = new ArrayList<>();

        for (LLMProvider provider : llmContext.getProviderFactory().getAllProviders()) {
            Map<String, Object> providerInfo = new LinkedHashMap<>();
            providerInfo.put("id", provider.getProviderId());
            providerInfo.put("name", provider.getDisplayName());
            providerInfo.put("available", provider.isAvailable());
            providerInfo.put("toolSupport", provider.getToolCallingSupport().name());
            providerList.add(providerInfo);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("providers", providerList);
        response.put("defaultProvider", llmContext.getProviderFactory().getDefaultProviderId());
        response.put("timestamp", Instant.now().toString());

        sendJson(resp, HttpServletResponse.SC_OK, response);
    }

    /**
     * Handles ending a conversation.
     */
    private void handleEndConversation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correlationId = generateCorrelationId();

        try {
            CorrelationContext.setCorrelationId(correlationId);

            // Authenticate
            AuthContext authContext;
            try {
                authContext = authService.authenticate(req);
            } catch (AuthenticationException e) {
                handleAuthenticationError(resp, e, correlationId, req);
                return;
            }

            if (conversationManager == null) {
                sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Chat feature is not configured", correlationId);
                return;
            }

            // Parse request body
            String body = req.getReader().lines().collect(Collectors.joining());
            EndConversationRequest endRequest = objectMapper.readValue(body, EndConversationRequest.class);

            if (endRequest.conversationId == null || endRequest.conversationId.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "conversationId is required", correlationId);
                return;
            }

            // End the conversation
            conversationManager.endConversation(endRequest.conversationId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("conversationId", endRequest.conversationId);
            response.put("status", "ended");
            response.put("correlationId", correlationId);
            response.put("timestamp", Instant.now().toString());

            sendJson(resp, HttpServletResponse.SC_OK, response);

            logger.info("[{}] Ended conversation {} for user {}",
                    correlationId, endRequest.conversationId, authContext.getUserId());

        } catch (Exception e) {
            logger.error("[{}] Unexpected error ending conversation: {}",
                    correlationId, e.getMessage(), e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error", correlationId);
        } finally {
            CorrelationContext.clear();
        }
    }

    // ========== Admin Endpoint Handlers ==========

    /**
     * Verifies Gateway admin authentication using Basic auth.
     * For now, accepts configured admin credentials.
     * TODO: Integrate with GatewayContext.getUserSourceManager() for proper auth.
     */
    private boolean isGatewayAdmin(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                return false;
            }

            // For now, validate against hardcoded dev credentials
            // TODO: Use Ignition's UserSourceManager for production
            // This should be replaced with proper integration to Gateway's auth system
            String username = parts[0];
            String password = parts[1];

            // Accept 'admin' user with 'password' for development
            // In production, this should validate against Ignition's user sources
            if ("admin".equals(username) && "password".equals(password)) {
                return true;
            }

            // Log failed attempt
            logger.warn("Admin auth failed for user: {}", username);
            return false;

        } catch (Exception e) {
            logger.warn("Failed to parse admin auth header: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handles GET requests to admin endpoints.
     */
    private void handleAdminGet(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
        if (!isGatewayAdmin(req)) {
            resp.setHeader("WWW-Authenticate", "Basic realm=\"LLM Gateway Admin\"");
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Gateway admin authentication required", null);
            return;
        }

        if (path.equals("/admin/api-keys")) {
            handleListApiKeys(resp);
        } else if (path.equals("/admin/providers")) {
            handleAdminListProviders(resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Admin endpoint not found: " + path, null);
        }
    }

    /**
     * Handles POST requests to admin endpoints.
     */
    private void handleAdminPost(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
        if (!isGatewayAdmin(req)) {
            resp.setHeader("WWW-Authenticate", "Basic realm=\"LLM Gateway Admin\"");
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Gateway admin authentication required", null);
            return;
        }

        if (path.equals("/admin/api-keys")) {
            handleCreateApiKey(req, resp);
        } else if (path.startsWith("/admin/providers/") && path.endsWith("/config")) {
            String providerId = extractProviderId(path);
            handleConfigureProvider(providerId, req, resp);
        } else if (path.equals("/admin/providers/default")) {
            handleSetDefaultProvider(req, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Admin endpoint not found: " + path, null);
        }
    }

    /**
     * Handles DELETE requests to admin endpoints.
     */
    private void handleAdminDelete(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
        if (!isGatewayAdmin(req)) {
            resp.setHeader("WWW-Authenticate", "Basic realm=\"LLM Gateway Admin\"");
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Gateway admin authentication required", null);
            return;
        }

        if (path.startsWith("/admin/api-keys/")) {
            String keyId = path.substring("/admin/api-keys/".length());
            handleRevokeApiKey(keyId, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Admin endpoint not found: " + path, null);
        }
    }

    /**
     * Extracts the provider ID from a path like /admin/providers/{providerId}/config.
     */
    private String extractProviderId(String path) {
        // Path is like "/admin/providers/claude/config"
        String withoutPrefix = path.substring("/admin/providers/".length());
        int slashIdx = withoutPrefix.indexOf('/');
        if (slashIdx > 0) {
            return withoutPrefix.substring(0, slashIdx);
        }
        return withoutPrefix;
    }

    /**
     * Handles listing all API keys.
     */
    private void handleListApiKeys(HttpServletResponse resp) throws IOException {
        ApiKeyManager apiKeyManager = llmContext.getApiKeyManager();
        Collection<ApiKey> keys = apiKeyManager.getAllKeys();

        List<Map<String, Object>> keyList = keys.stream()
                .map(k -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", k.getId());
                    info.put("name", k.getName());
                    info.put("keyPrefix", k.getKeyPrefix());
                    info.put("permissions", k.getPermissions().stream()
                            .map(Permission::getCode)
                            .sorted()
                            .collect(Collectors.toList()));
                    info.put("enabled", k.isEnabled());
                    info.put("createdAt", k.getCreatedAt().toString());
                    info.put("lastUsedAt", k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : null);
                    info.put("expiresAt", k.getExpiresAt() != null ? k.getExpiresAt().toString() : null);
                    return info;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("keys", keyList);
        response.put("count", keyList.size());
        response.put("timestamp", Instant.now().toString());

        sendJson(resp, HttpServletResponse.SC_OK, response);
    }

    /**
     * Handles creating a new API key.
     */
    private void handleCreateApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().collect(Collectors.joining());
        JsonNode json = objectMapper.readTree(body);

        String name = json.path("name").asText("unnamed-key");

        // Parse permissions
        Set<Permission> permissions = new HashSet<>();
        JsonNode permsNode = json.path("permissions");
        if (permsNode.isArray()) {
            for (JsonNode perm : permsNode) {
                try {
                    permissions.add(Permission.fromCode(perm.asText()));
                } catch (IllegalArgumentException e) {
                    // Try as enum name
                    try {
                        permissions.add(Permission.valueOf(perm.asText()));
                    } catch (IllegalArgumentException e2) {
                        logger.warn("Unknown permission: {}", perm.asText());
                    }
                }
            }
        }

        // Default to read-only if no permissions specified
        if (permissions.isEmpty()) {
            permissions.add(Permission.TAG_READ);
            permissions.add(Permission.VIEW_READ);
            permissions.add(Permission.PROJECT_READ);
        }

        // Create the key
        ApiKeyManager apiKeyManager = llmContext.getApiKeyManager();
        ApiKeyManager.ApiKeyCreationResult result = apiKeyManager.createKey(name, permissions);

        // Build response with the raw key (only time it's visible!)
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("keyId", result.getApiKey().getId());
        response.put("keyPrefix", result.getApiKey().getKeyPrefix());
        response.put("rawKey", result.getRawKey());  // ONLY SHOWN ONCE
        response.put("permissions", permissions.stream()
                .map(Permission::getCode)
                .sorted()
                .collect(Collectors.toList()));
        response.put("warning", "Save this key now! It cannot be retrieved later.");
        response.put("timestamp", Instant.now().toString());

        logger.info("Created API key: {} (id={}, permissions={})",
                name, result.getApiKey().getId(), permissions.size());

        sendJson(resp, HttpServletResponse.SC_CREATED, response);
    }

    /**
     * Handles revoking an API key.
     */
    private void handleRevokeApiKey(String keyId, HttpServletResponse resp) throws IOException {
        ApiKeyManager apiKeyManager = llmContext.getApiKeyManager();
        boolean revoked = apiKeyManager.revokeKey(keyId);

        Map<String, Object> response = new LinkedHashMap<>();
        if (revoked) {
            response.put("success", true);
            response.put("message", "API key revoked");
            response.put("keyId", keyId);
            logger.info("Revoked API key: {}", keyId);
            sendJson(resp, HttpServletResponse.SC_OK, response);
        } else {
            response.put("success", false);
            response.put("error", "API key not found");
            response.put("keyId", keyId);
            sendJson(resp, HttpServletResponse.SC_NOT_FOUND, response);
        }
    }

    /**
     * Handles listing providers with configuration status (admin view).
     */
    private void handleAdminListProviders(HttpServletResponse resp) throws IOException {
        LLMProviderFactory providerFactory = llmContext.getProviderFactory();

        // List known provider types and their status
        List<String> knownProviders = List.of("claude", "openai", "ollama");
        List<Map<String, Object>> providers = new ArrayList<>();

        for (String id : knownProviders) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", id);

            Optional<LLMProvider> provider = providerFactory.getProvider(id);
            if (provider.isPresent()) {
                LLMProvider p = provider.get();
                info.put("name", p.getDisplayName());
                info.put("available", p.isAvailable());
                info.put("configured", true);
                info.put("toolSupport", p.getToolCallingSupport().name());
            } else {
                info.put("name", getProviderDisplayName(id));
                info.put("available", false);
                info.put("configured", false);
                info.put("toolSupport", "UNKNOWN");
            }

            providers.add(info);
        }

        // Find default provider
        String defaultProvider = providerFactory.getDefaultProviderId();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("providers", providers);
        response.put("defaultProvider", defaultProvider);
        response.put("timestamp", Instant.now().toString());

        sendJson(resp, HttpServletResponse.SC_OK, response);
    }

    /**
     * Gets a display name for a provider ID.
     */
    private String getProviderDisplayName(String providerId) {
        switch (providerId) {
            case "claude": return "Claude (Anthropic)";
            case "openai": return "OpenAI";
            case "ollama": return "Ollama (Local)";
            default: return providerId;
        }
    }

    /**
     * Handles configuring an LLM provider.
     */
    private void handleConfigureProvider(String providerId, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().collect(Collectors.joining());
        JsonNode json = objectMapper.readTree(body);

        // Build provider config
        ProviderConfig.Builder configBuilder = ProviderConfig.builder()
                .providerId(providerId)
                .enabled(json.path("enabled").asBoolean(true));

        if (json.has("apiKey")) {
            configBuilder.apiKey(json.path("apiKey").asText());
        }
        if (json.has("apiBaseUrl")) {
            configBuilder.apiBaseUrl(json.path("apiBaseUrl").asText());
        }
        if (json.has("defaultModel")) {
            configBuilder.defaultModel(json.path("defaultModel").asText());
        }
        if (json.has("maxTokens")) {
            configBuilder.maxTokens(json.path("maxTokens").asInt());
        }
        if (json.has("temperature")) {
            configBuilder.temperature(json.path("temperature").asDouble());
        }
        if (json.has("requestsPerMinute")) {
            configBuilder.requestsPerMinute(json.path("requestsPerMinute").asInt());
        }
        if (json.has("tokensPerMinute")) {
            configBuilder.tokensPerMinute(json.path("tokensPerMinute").asInt());
        }

        ProviderConfig config = configBuilder.build();

        // Register or update the provider
        LLMProviderFactory providerFactory = llmContext.getProviderFactory();
        providerFactory.registerProvider(config);

        // Check if provider is now available
        Optional<LLMProvider> provider = providerFactory.getProvider(providerId);
        boolean available = provider.isPresent() && provider.get().isAvailable();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("providerId", providerId);
        response.put("available", available);
        response.put("message", available ?
                "Provider configured and available" :
                "Provider configured but not available (check API key)");
        response.put("timestamp", Instant.now().toString());

        logger.info("Configured provider: {} (available={})", providerId, available);

        sendJson(resp, HttpServletResponse.SC_OK, response);
    }

    /**
     * Handles setting the default LLM provider.
     */
    private void handleSetDefaultProvider(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().collect(Collectors.joining());
        JsonNode json = objectMapper.readTree(body);

        String providerId = json.path("providerId").asText(null);
        if (providerId == null || providerId.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "providerId is required", null);
            return;
        }

        LLMProviderFactory providerFactory = llmContext.getProviderFactory();
        Optional<LLMProvider> provider = providerFactory.getProvider(providerId);

        if (!provider.isPresent()) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Provider not found: " + providerId, null);
            return;
        }

        if (!provider.get().isAvailable()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Provider is not available (not configured or unavailable): " + providerId, null);
            return;
        }

        providerFactory.setDefaultProvider(providerId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("defaultProvider", providerId);
        response.put("message", "Default provider set to: " + getProviderDisplayName(providerId));
        response.put("timestamp", Instant.now().toString());

        logger.info("Set default provider to: {}", providerId);

        sendJson(resp, HttpServletResponse.SC_OK, response);
    }

    // ========== Request/Response DTOs ==========

    /**
     * Chat request DTO.
     */
    static class ChatRequest {
        public String conversationId;
        public String message;

        public String getConversationId() { return conversationId; }
        public String getMessage() { return message; }
    }

    /**
     * Chat response DTO.
     */
    static class ChatResponse {
        public String conversationId;
        public String correlationId;
        public String message;
        public List<ActionResult> actionResults;
        public String timestamp;
    }

    /**
     * End conversation request DTO.
     */
    static class EndConversationRequest {
        public String conversationId;
    }

    /**
     * Handles authentication errors.
     */
    private void handleAuthenticationError(HttpServletResponse resp, AuthenticationException e,
                                           String correlationId, HttpServletRequest req) throws IOException {
        logger.warn("[{}] Authentication failed from {}: {}",
                correlationId, getClientAddress(req), e.getMessage());

        // Log the auth failure
        llmContext.getAuditLogger().logAuthFailure(
                correlationId,
                getClientAddress(req),
                e.getReason().name(),
                e.getMessage()
        );

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "Authentication failed");
        error.put("message", e.getMessage());
        error.put("reason", e.getReason().name());
        error.put("correlationId", correlationId);
        error.put("timestamp", Instant.now().toString());

        // Add WWW-Authenticate header for 401 responses
        resp.setHeader("WWW-Authenticate", "Bearer realm=\"LLM Gateway\"");

        sendJson(resp, HttpServletResponse.SC_UNAUTHORIZED, error);
    }

    /**
     * Handles authorization errors.
     */
    private void handleAuthorizationError(HttpServletResponse resp, AuthorizationException e,
                                          String correlationId, AuthContext auth, Action action) throws IOException {
        logger.warn("[{}] Authorization denied for user {} on {}/{}: {}",
                correlationId, auth.getUserId(), action.getResourceType(),
                action.getActionType(), e.getMessage());

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "Authorization denied");
        error.put("message", e.getMessage());
        error.put("correlationId", correlationId);
        error.put("resourceType", action.getResourceType());
        error.put("actionType", action.getActionType());
        if (e.getRequiredPermission() != null) {
            error.put("requiredPermission", e.getRequiredPermission().getCode());
        }
        error.put("timestamp", Instant.now().toString());

        sendJson(resp, HttpServletResponse.SC_FORBIDDEN, error);
    }

    /**
     * Maps ActionResult status to HTTP status code.
     */
    private int mapResultToHttpStatus(ActionResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
            case DRY_RUN:
                return HttpServletResponse.SC_OK;
            case PARTIAL:
                return 207; // Multi-Status (WebDAV)
            case PENDING_CONFIRMATION:
                return HttpServletResponse.SC_ACCEPTED; // 202
            case FAILURE:
                // Check if it's a not-found error
                if (result.getErrors() != null &&
                        result.getErrors().stream().anyMatch(e -> e.contains("NOT_FOUND"))) {
                    return HttpServletResponse.SC_NOT_FOUND;
                }
                // Check if it's a conflict error
                if (result.getErrors() != null &&
                        result.getErrors().stream().anyMatch(e -> e.contains("CONFLICT"))) {
                    return HttpServletResponse.SC_CONFLICT;
                }
                return HttpServletResponse.SC_BAD_REQUEST;
            default:
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Generates a unique correlation ID for request tracking.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the client IP address, accounting for proxies.
     */
    private String getClientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Sends a JSON response.
     */
    private void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(resp.getWriter(), data);
    }

    /**
     * Sends an error response.
     */
    private void sendError(HttpServletResponse resp, int status, String message,
                           String correlationId) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        error.put("status", status);
        if (correlationId != null) {
            error.put("correlationId", correlationId);
        }
        error.put("timestamp", Instant.now().toString());
        sendJson(resp, status, error);
    }
}
