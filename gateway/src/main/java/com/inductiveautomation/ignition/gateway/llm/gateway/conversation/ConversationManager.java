package com.inductiveautomation.ignition.gateway.llm.gateway.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.CorrelationContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthorizationException;
import com.inductiveautomation.ignition.gateway.llm.gateway.execution.ActionExecutor;
import com.inductiveautomation.ignition.gateway.llm.gateway.execution.ActionParser;
import com.inductiveautomation.ignition.gateway.llm.gateway.policy.EnvironmentMode;
import com.inductiveautomation.ignition.gateway.llm.gateway.policy.PolicyEngine;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMMessage;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProvider;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMProviderFactory;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMRequest;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMResponse;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolDefinition;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.ProviderException;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.StreamingCallback;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.SystemPromptBuilder;
import com.inductiveautomation.ignition.gateway.llm.gateway.schema.ActionSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages multi-turn conversations with LLMs.
 * Handles message processing, tool execution, and context management.
 */
public class ConversationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    private static final int MAX_CONTEXT_TOKENS = 100000;
    private static final Duration CONVERSATION_TIMEOUT = Duration.ofHours(1);
    private static final int MAX_TOOL_ITERATIONS = 10; // Prevent infinite tool loops

    private final Map<String, Conversation> activeConversations = new ConcurrentHashMap<>();
    private final LLMProviderFactory providerFactory;
    private final ActionExecutor actionExecutor;
    private final AuditLogger auditLogger;
    private final PolicyEngine policyEngine;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService cleanupScheduler;

    public ConversationManager(
            LLMProviderFactory providerFactory,
            ActionExecutor actionExecutor,
            AuditLogger auditLogger,
            PolicyEngine policyEngine) {
        this.providerFactory = providerFactory;
        this.actionExecutor = actionExecutor;
        this.auditLogger = auditLogger;
        this.policyEngine = policyEngine;
        this.objectMapper = new ObjectMapper();

        // Schedule periodic cleanup of expired conversations
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConversationCleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredConversations,
                1, 10, TimeUnit.MINUTES);

        logger.info("ConversationManager initialized");
    }

    /**
     * Process a user message and return the assistant's response.
     */
    public ConversationResponse processMessage(
            String conversationId,
            String userMessage,
            AuthContext auth) throws ProviderException {

        String correlationId = CorrelationContext.getCurrentCorrelationId();

        // Get or create conversation
        Conversation conversation = activeConversations.computeIfAbsent(
                conversationId,
                id -> new Conversation(id, auth)
        );

        // Security: Verify auth matches the conversation owner
        if (!conversation.getAuthContext().getKeyId().equals(auth.getKeyId())) {
            logger.warn("Attempt to access conversation {} with different API key", conversationId);
            throw new SecurityException("Conversation belongs to a different API key");
        }

        // Add user message
        conversation.addMessage(LLMMessage.user(userMessage));

        // Log the user message
        auditLogger.logConversationMessage(correlationId, conversationId, "user", auth);

        try {
            // Process with LLM
            ConversationResponse response = processWithLLM(conversation, auth, correlationId);

            // Update activity timestamp
            conversation.setLastActivity(java.time.Instant.now());

            // Prune context if needed
            pruneContextIfNeeded(conversation);

            return response;

        } catch (ProviderException e) {
            logger.error("LLM provider error in conversation {}: {}", conversationId, e.getMessage());
            throw e;
        }
    }

    /**
     * Process a user message with streaming response support.
     * Tokens are streamed back via the handler as they're generated.
     */
    public void processMessageStreaming(
            String conversationId,
            String userMessage,
            AuthContext auth,
            StreamingResponseHandler handler) {

        String correlationId = CorrelationContext.getCurrentCorrelationId();

        try {
            // Get or create conversation
            Conversation conversation = activeConversations.computeIfAbsent(
                    conversationId,
                    id -> new Conversation(id, auth)
            );

            // Security: Verify auth matches the conversation owner
            if (!conversation.getAuthContext().getKeyId().equals(auth.getKeyId())) {
                logger.warn("Attempt to access conversation {} with different API key", conversationId);
                handler.onError(new SecurityException("Conversation belongs to a different API key"));
                return;
            }

            // Add user message
            conversation.addMessage(LLMMessage.user(userMessage));

            // Log the user message
            auditLogger.logConversationMessage(correlationId, conversationId, "user", auth);

            // Process with LLM using streaming
            processWithLLMStreaming(conversation, auth, correlationId, handler);

            // Update activity timestamp
            conversation.setLastActivity(java.time.Instant.now());

            // Prune context if needed
            pruneContextIfNeeded(conversation);

        } catch (Exception e) {
            logger.error("Error in streaming conversation {}: {}", conversationId, e.getMessage(), e);
            handler.onError(e);
        }
    }

    /**
     * Sends the conversation to the LLM with streaming and processes the response.
     */
    private void processWithLLMStreaming(
            Conversation conversation,
            AuthContext auth,
            String correlationId,
            StreamingResponseHandler handler) {

        LLMProvider provider = providerFactory.getDefaultProvider();
        EnvironmentMode envMode = policyEngine.getEnvironmentMode();

        // Build tools list based on permissions
        List<LLMToolDefinition> tools = ActionSchemas.getToolsForPermissions(auth.getPermissions());

        // Build request
        LLMRequest request = LLMRequest.builder()
                .conversationId(conversation.getId())
                .messages(conversation.getMessages())
                .tools(tools)
                .systemPrompt(SystemPromptBuilder.buildSystemPrompt(auth, envMode))
                .build();

        // Create streaming callback that bridges to the handler
        final StringBuilder contentBuilder = new StringBuilder();
        final List<LLMToolCall> pendingToolCalls = new ArrayList<>();
        final List<ActionResult> actionResults = new ArrayList<>();

        try {
            provider.chatStreaming(request, new StreamingCallback() {
            @Override
            public void onToken(String token) {
                contentBuilder.append(token);
                handler.onToken(token);
            }

            @Override
            public void onToolCall(LLMToolCall toolCall) {
                pendingToolCalls.add(toolCall);
                handler.onToolCallStart(toolCall);

                // Execute the tool call
                try {
                    Action action = ActionParser.parseToolCall(toolCall);
                    policyEngine.authorize(auth, action);
                    ActionResult result = actionExecutor.execute(action, auth);
                    actionResults.add(result);

                    // Log tool execution
                    auditLogger.logToolExecution(correlationId, toolCall.getName(),
                            result.getStatus().name(), auth);

                    handler.onToolCallComplete(toolCall, result);
                } catch (ActionParser.ParseException e) {
                    logger.warn("Failed to parse tool call {}: {}", toolCall.getName(), e.getMessage());
                    ActionResult errorResult = ActionResult.failure(correlationId,
                            "Failed to parse tool arguments: " + e.getMessage(),
                            java.util.Collections.singletonList(e.getMessage()));
                    handler.onToolCallComplete(toolCall, errorResult);
                } catch (AuthorizationException e) {
                    logger.warn("Authorization denied for tool {}: {}", toolCall.getName(), e.getMessage());
                    ActionResult errorResult = ActionResult.failure(correlationId,
                            "Permission denied: " + e.getMessage(),
                            java.util.Collections.singletonList(e.getMessage()));
                    handler.onToolCallComplete(toolCall, errorResult);
                } catch (Exception e) {
                    logger.error("Error executing tool {}: {}", toolCall.getName(), e.getMessage(), e);
                    ActionResult errorResult = ActionResult.failure(correlationId,
                            "Tool execution error: " + e.getMessage(),
                            java.util.Collections.singletonList(e.getMessage()));
                    handler.onToolCallComplete(toolCall, errorResult);
                }
            }

            @Override
            public void onComplete(LLMResponse fullResponse) {
                // Add assistant message to conversation
                conversation.addMessage(LLMMessage.builder()
                        .role(LLMMessage.MessageRole.ASSISTANT)
                        .content(contentBuilder.toString())
                        .toolCalls(pendingToolCalls.isEmpty() ? null : pendingToolCalls)
                        .build());

                // Build final response
                ConversationResponse response = ConversationResponse.builder()
                        .message(contentBuilder.toString())
                        .actionResults(actionResults.isEmpty() ? null : actionResults)
                        .tokensUsed(fullResponse.getTotalTokens())
                        .build();

                handler.onComplete(response);
            }

            @Override
            public void onError(ProviderException error) {
                handler.onError(error);
            }
            });
        } catch (ProviderException e) {
            logger.error("Provider error during streaming: {}", e.getMessage(), e);
            handler.onError(e);
        }
    }

    /**
     * Sends the conversation to the LLM and processes the response.
     */
    private ConversationResponse processWithLLM(
            Conversation conversation,
            AuthContext auth,
            String correlationId) throws ProviderException {

        LLMProvider provider = providerFactory.getDefaultProvider();
        EnvironmentMode envMode = policyEngine.getEnvironmentMode();

        // Build tools list based on permissions
        List<LLMToolDefinition> tools = ActionSchemas.getToolsForPermissions(auth.getPermissions());

        // Build request
        LLMRequest request = LLMRequest.builder()
                .conversationId(conversation.getId())
                .messages(conversation.getMessages())
                .tools(tools)
                .systemPrompt(SystemPromptBuilder.buildSystemPrompt(auth, envMode))
                .build();

        // Send to LLM
        LLMResponse llmResponse = provider.chat(request);

        // Process response (may involve tool calls)
        return processLLMResponse(conversation, llmResponse, auth, correlationId, provider, 0);
    }

    /**
     * Processes an LLM response, executing any tool calls.
     */
    private ConversationResponse processLLMResponse(
            Conversation conversation,
            LLMResponse llmResponse,
            AuthContext auth,
            String correlationId,
            LLMProvider provider,
            int iteration) throws ProviderException {

        // Check for runaway tool loops
        if (iteration >= MAX_TOOL_ITERATIONS) {
            logger.warn("Max tool iterations reached for conversation {}", conversation.getId());
            return ConversationResponse.builder()
                    .message("I've reached the maximum number of operations. " +
                            "Please review the results and let me know if you need anything else.")
                    .build();
        }

        // If no tool calls, return the text response
        if (!llmResponse.hasToolCalls()) {
            conversation.addMessage(LLMMessage.builder()
                    .role(LLMMessage.MessageRole.ASSISTANT)
                    .content(llmResponse.getContent())
                    .build());

            return ConversationResponse.builder()
                    .message(llmResponse.getContent())
                    .build();
        }

        // Process tool calls
        List<ActionResult> actionResults = new ArrayList<>();
        List<LLMMessage> toolResultMessages = new ArrayList<>();

        // Add assistant message with tool calls
        conversation.addMessage(LLMMessage.builder()
                .role(LLMMessage.MessageRole.ASSISTANT)
                .content(llmResponse.getContent())
                .toolCalls(llmResponse.getToolCalls())
                .build());

        for (LLMToolCall toolCall : llmResponse.getToolCalls()) {
            logger.debug("Processing tool call: {} ({})", toolCall.getName(), toolCall.getId());

            try {
                // Parse tool call into action
                Action action = ActionParser.parseToolCall(toolCall);

                // Set correlation ID on action
                // Note: Action is immutable, so we need to handle this differently
                // For now, we'll use the correlation context

                // Authorize the action
                policyEngine.authorize(auth, action);

                // Execute
                ActionResult result = actionExecutor.execute(action, auth);
                actionResults.add(result);

                // Log tool execution
                auditLogger.logToolExecution(correlationId, toolCall.getName(),
                        result.getStatus().name(), auth);

                // Build tool result message
                toolResultMessages.add(LLMMessage.toolResult(
                        toolCall.getId(),
                        formatActionResult(result)
                ));

            } catch (ActionParser.ParseException e) {
                logger.warn("Failed to parse tool call {}: {}", toolCall.getName(), e.getMessage());
                toolResultMessages.add(LLMMessage.toolResult(
                        toolCall.getId(),
                        "ERROR: Failed to parse tool arguments - " + e.getMessage()
                ));

            } catch (AuthorizationException e) {
                logger.warn("Authorization denied for tool {}: {}", toolCall.getName(), e.getMessage());
                toolResultMessages.add(LLMMessage.toolResult(
                        toolCall.getId(),
                        "ERROR: Permission denied - " + e.getMessage()
                ));

            } catch (Exception e) {
                logger.error("Error executing tool {}: {}", toolCall.getName(), e.getMessage(), e);
                toolResultMessages.add(LLMMessage.toolResult(
                        toolCall.getId(),
                        "ERROR: " + e.getMessage()
                ));
            }
        }

        // Add tool result messages to conversation
        for (LLMMessage msg : toolResultMessages) {
            conversation.addMessage(msg);
        }

        // Send tool results back to LLM for final response
        LLMRequest followUpRequest = LLMRequest.builder()
                .conversationId(conversation.getId())
                .messages(conversation.getMessages())
                .tools(ActionSchemas.getToolsForPermissions(auth.getPermissions()))
                .systemPrompt(SystemPromptBuilder.buildSystemPrompt(auth, policyEngine.getEnvironmentMode()))
                .build();

        LLMResponse finalResponse = provider.chat(followUpRequest);

        // Recursively process if there are more tool calls
        if (finalResponse.hasToolCalls()) {
            return processLLMResponse(conversation, finalResponse, auth, correlationId,
                    provider, iteration + 1);
        }

        // Add final response to conversation
        conversation.addMessage(LLMMessage.builder()
                .role(LLMMessage.MessageRole.ASSISTANT)
                .content(finalResponse.getContent())
                .build());

        return ConversationResponse.builder()
                .message(finalResponse.getContent())
                .actionResults(actionResults)
                .build();
    }

    /**
     * Formats an ActionResult for the LLM.
     */
    private String formatActionResult(ActionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(result.getStatus()).append("\n");
        sb.append("Message: ").append(result.getMessage()).append("\n");

        if (result.getData() != null) {
            sb.append("Data:\n");
            try {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(result.getData()));
            } catch (JsonProcessingException e) {
                sb.append(result.getData().toString());
            }
        }

        if (!result.getWarnings().isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String warning : result.getWarnings()) {
                sb.append("- ").append(warning).append("\n");
            }
        }

        if (!result.getErrors().isEmpty()) {
            sb.append("\nErrors:\n");
            for (String error : result.getErrors()) {
                sb.append("- ").append(error).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Prunes conversation context if it exceeds token limits.
     */
    private void pruneContextIfNeeded(Conversation conversation) {
        LLMProvider provider = providerFactory.getDefaultProvider();

        int totalTokens = 0;
        for (LLMMessage msg : conversation.getMessages()) {
            if (msg.getContent() != null) {
                totalTokens += provider.estimateTokens(msg.getContent());
            }
        }

        if (totalTokens > MAX_CONTEXT_TOKENS) {
            logger.info("Pruning conversation {} context ({} tokens)",
                    conversation.getId(), totalTokens);

            List<LLMMessage> messages = conversation.getMessages();
            int keepRecent = 10;

            if (messages.size() > keepRecent) {
                // Keep system context and summarize old messages
                List<LLMMessage> toKeep = messages.subList(
                        messages.size() - keepRecent, messages.size());

                conversation.clearMessages();

                // Add a summary message
                conversation.addMessage(LLMMessage.system(
                        "Earlier context has been summarized. " +
                        "Continue assisting the user with their Ignition Gateway tasks."));

                // Add recent messages
                for (LLMMessage msg : toKeep) {
                    conversation.addMessage(msg);
                }
            }
        }
    }

    /**
     * Gets a conversation by ID.
     */
    public Conversation getConversation(String conversationId) {
        return activeConversations.get(conversationId);
    }

    /**
     * Ends a conversation and removes it from memory.
     */
    public void endConversation(String conversationId) {
        Conversation removed = activeConversations.remove(conversationId);
        if (removed != null) {
            logger.info("Ended conversation: {} ({} messages)",
                    conversationId, removed.getMessageCount());
        }
    }

    /**
     * Cleans up expired conversations.
     */
    private void cleanupExpiredConversations() {
        long timeoutMillis = CONVERSATION_TIMEOUT.toMillis();
        List<String> expired = new ArrayList<>();

        for (Map.Entry<String, Conversation> entry : activeConversations.entrySet()) {
            if (entry.getValue().isExpired(timeoutMillis)) {
                expired.add(entry.getKey());
            }
        }

        for (String id : expired) {
            activeConversations.remove(id);
            logger.info("Cleaned up expired conversation: {}", id);
        }

        if (!expired.isEmpty()) {
            logger.info("Cleaned up {} expired conversations", expired.size());
        }
    }

    /**
     * Gets the count of active conversations.
     */
    public int getActiveConversationCount() {
        return activeConversations.size();
    }

    /**
     * Shuts down the conversation manager.
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        activeConversations.clear();
        logger.info("ConversationManager shutdown complete");
    }
}
