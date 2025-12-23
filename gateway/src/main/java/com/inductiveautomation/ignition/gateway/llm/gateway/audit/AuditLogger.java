package com.inductiveautomation.ignition.gateway.llm.gateway.audit;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides append-only audit logging for all LLM Gateway operations.
 * All actions, results, and system events are logged for traceability.
 */
public class AuditLogger {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogger.class);

    private final GatewayContext gatewayContext;

    // Correlation context for tracking related operations
    private final Map<String, CorrelationContext> activeCorrelations = new ConcurrentHashMap<>();

    public AuditLogger(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
        logger.debug("AuditLogger initialized");
    }

    /**
     * Logs an action request before execution.
     */
    public void logActionRequest(Action action, String userId, String clientInfo) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(action.getCorrelationId())
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("ACTION_REQUEST")
                .userId(userId)
                .resourceType(action.getResourceType())
                .resourcePath(action.getResourcePath())
                .actionType(action.getActionType())
                .details(Map.of(
                        "options", action.getOptions().toString(),
                        "clientInfo", clientInfo != null ? clientInfo : "unknown",
                        "isDestructive", action.isDestructive(),
                        "requiresConfirmation", action.requiresConfirmation()
                ))
                .build();

        writeAuditEntry(entry);

        // Track active correlation
        activeCorrelations.put(action.getCorrelationId(),
                new CorrelationContext(action.getCorrelationId(), Instant.now(), userId));
    }

    /**
     * Logs the result of an action execution.
     */
    public void logActionResult(String correlationId, ActionResult result) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("ACTION_RESULT")
                .details(Map.of(
                        "status", result.getStatus().name(),
                        "message", result.getMessage() != null ? result.getMessage() : "",
                        "durationMs", result.getDurationMs(),
                        "warnings", result.getWarnings(),
                        "errors", result.getErrors()
                ))
                .build();

        writeAuditEntry(entry);

        // Clean up correlation context
        activeCorrelations.remove(correlationId);
    }

    /**
     * Logs an authorization decision.
     */
    public void logAuthorizationEvent(String correlationId, String userId,
                                       String action, String resource,
                                       boolean allowed, String reason) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_AUTH)
                .eventType(allowed ? "AUTHORIZATION_GRANTED" : "AUTHORIZATION_DENIED")
                .userId(userId)
                .resourcePath(resource)
                .actionType(action)
                .details(Map.of(
                        "allowed", allowed,
                        "reason", reason != null ? reason : ""
                ))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs an authentication failure.
     */
    public void logAuthFailure(String correlationId, String clientAddress,
                               String reason, String message) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_AUTH)
                .eventType("AUTHENTICATION_FAILED")
                .details(Map.of(
                        "clientAddress", clientAddress != null ? clientAddress : "unknown",
                        "reason", reason != null ? reason : "UNKNOWN",
                        "message", message != null ? message : ""
                ))
                .build();

        writeAuditEntry(entry);

        // Also log at WARN level for monitoring
        logger.warn("[AUDIT] Authentication failure from {} - reason: {}, message: {}",
                clientAddress, reason, message);
    }

    /**
     * Logs a policy decision.
     */
    public void logPolicyEvent(String correlationId, String policyName,
                                String decision, String reason) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_POLICY)
                .eventType("POLICY_DECISION")
                .details(Map.of(
                        "policyName", policyName,
                        "decision", decision,
                        "reason", reason != null ? reason : ""
                ))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs a system event (startup, shutdown, configuration change).
     */
    public void logSystemEvent(String eventType, String message, Map<String, Object> details) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(UUID.randomUUID().toString())
                .category(LLMGatewayConstants.AUDIT_CATEGORY_SYSTEM)
                .eventType(eventType)
                .details(details != null ? details : Map.of("message", message))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Writes an audit entry to the persistent log.
     */
    private void writeAuditEntry(AuditEntry entry) {
        // Log to SLF4J for immediate visibility
        logger.info("[AUDIT] {} | {} | {} | corr={} | user={} | resource={}",
                entry.getCategory(),
                entry.getEventType(),
                entry.getActionType() != null ? entry.getActionType() : "-",
                entry.getCorrelationId(),
                entry.getUserId() != null ? entry.getUserId() : "-",
                entry.getResourcePath() != null ? entry.getResourcePath() : "-");

        // TODO: Persist to database via Ignition's audit profile or custom table
        // This ensures audit logs survive restarts and can be queried later
        // gatewayContext.getAuditManager().addEvent(...);

        if (logger.isDebugEnabled()) {
            logger.debug("[AUDIT DETAILS] {}", entry.getDetails());
        }
    }

    /**
     * Returns the active correlation context for a given ID.
     */
    public CorrelationContext getCorrelationContext(String correlationId) {
        return activeCorrelations.get(correlationId);
    }

    /**
     * Logs a conversation message.
     */
    public void logConversationMessage(String correlationId, String conversationId,
                                        String role, com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext auth) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("CONVERSATION_MESSAGE")
                .userId(auth.getUserId())
                .details(Map.of(
                        "conversationId", conversationId,
                        "role", role
                ))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs a tool execution within a conversation.
     */
    public void logToolExecution(String correlationId, String toolName,
                                  String resultStatus, com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext auth) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(correlationId)
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("TOOL_EXECUTION")
                .userId(auth.getUserId())
                .actionType(toolName)
                .details(Map.of(
                        "tool", toolName,
                        "result", resultStatus
                ))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs a resource creation.
     */
    public void logResourceCreated(String resourceType, String resourcePath,
                                    com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext auth) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(CorrelationContext.getCurrentCorrelationId())
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("RESOURCE_CREATED")
                .userId(auth.getUserId())
                .resourceType(resourceType)
                .resourcePath(resourcePath)
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs a resource update.
     */
    public void logResourceUpdated(String resourceType, String resourcePath,
                                    com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext auth,
                                    String comment) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(CorrelationContext.getCurrentCorrelationId())
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("RESOURCE_UPDATED")
                .userId(auth.getUserId())
                .resourceType(resourceType)
                .resourcePath(resourcePath)
                .details(Map.of("comment", comment != null ? comment : ""))
                .build();

        writeAuditEntry(entry);
    }

    /**
     * Logs a resource deletion.
     */
    public void logResourceDeleted(String resourceType, String resourcePath,
                                    com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext auth) {
        AuditEntry entry = AuditEntry.builder()
                .correlationId(CorrelationContext.getCurrentCorrelationId())
                .category(LLMGatewayConstants.AUDIT_CATEGORY_ACTION)
                .eventType("RESOURCE_DELETED")
                .userId(auth.getUserId())
                .resourceType(resourceType)
                .resourcePath(resourcePath)
                .build();

        writeAuditEntry(entry);
    }
}
