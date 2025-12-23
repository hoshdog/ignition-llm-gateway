package com.inductiveautomation.ignition.gateway.llm.gateway.execution;

import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.LLMGatewayContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthorizationException;
import com.inductiveautomation.ignition.gateway.llm.gateway.execution.validators.ActionValidator;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.NamedQueryResourceHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ProjectResourceHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ResourceHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ScriptResourceHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.TagResourceHandler;
import com.inductiveautomation.ignition.gateway.llm.gateway.resources.ViewResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes validated and authorized actions against Ignition resources.
 * Routes actions to appropriate ResourceHandlers.
 */
public class ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ActionExecutor.class);

    private final LLMGatewayContext context;
    private final ActionValidator validator;
    private final Map<String, ResourceHandler> handlers;

    public ActionExecutor(LLMGatewayContext llmContext) {
        this.context = llmContext;
        this.validator = new ActionValidator();
        this.handlers = new HashMap<>();

        // Register resource handlers
        registerDefaultHandlers();
    }

    /**
     * Registers the default resource handlers.
     */
    private void registerDefaultHandlers() {
        // Tag handler
        registerHandler(new TagResourceHandler(
                context.getGatewayContext(),
                context.getAuditLogger()
        ));

        // Project handler
        registerHandler(new ProjectResourceHandler(
                context.getGatewayContext(),
                context.getAuditLogger()
        ));

        // View handler
        registerHandler(new ViewResourceHandler(
                context.getGatewayContext(),
                context.getAuditLogger()
        ));

        // Script handler
        registerHandler(new ScriptResourceHandler(
                context.getGatewayContext(),
                context.getAuditLogger()
        ));

        // Named query handler
        registerHandler(new NamedQueryResourceHandler(
                context.getGatewayContext(),
                context.getAuditLogger()
        ));

        logger.info("Registered {} resource handlers: {}", handlers.size(), handlers.keySet());
    }

    /**
     * Registers a resource handler.
     */
    public void registerHandler(ResourceHandler handler) {
        handlers.put(handler.getResourceType(), handler);
        logger.debug("Registered handler for resource type: {}", handler.getResourceType());
    }

    /**
     * Executes an action with full authentication context.
     */
    public ActionResult execute(Action action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        logger.debug("Executing action {} for user {}", correlationId, auth.getUserId());

        try {
            // Step 1: Validate the action
            ValidationResult validationResult = validator.validate(action);
            if (!validationResult.isValid()) {
                logger.warn("Action {} failed validation: {}", correlationId, validationResult.getErrors());
                return ActionResult.failure(
                        correlationId,
                        "Action validation failed",
                        validationResult.getErrors().stream()
                                .map(ValidationResult.ValidationError::toString)
                                .toList()
                );
            }

            // Step 2: Check authorization using PolicyEngine
            try {
                context.getPolicyEngine().authorize(auth, action);
            } catch (AuthorizationException e) {
                logger.warn("Action {} denied for user {}: {}",
                        correlationId, auth.getUserId(), e.getMessage());

                context.getAuditLogger().logAuthorizationEvent(
                        correlationId, auth.getUserId(), action.getActionType(),
                        action.getResourcePath(), false, e.getMessage()
                );

                return ActionResult.failure(
                        correlationId,
                        "Authorization denied: " + e.getMessage(),
                        Collections.singletonList(e.getMessage())
                );
            }

            // Log authorization success
            context.getAuditLogger().logAuthorizationEvent(
                    correlationId, auth.getUserId(), action.getActionType(),
                    action.getResourcePath(), true, "Authorization granted"
            );

            // Step 3: Find the appropriate handler
            ResourceHandler handler = handlers.get(action.getResourceType());
            if (handler == null) {
                logger.error("No handler for resource type: {}", action.getResourceType());
                return ActionResult.failure(
                        correlationId,
                        "Unsupported resource type: " + action.getResourceType(),
                        Collections.singletonList("No handler registered for this resource type")
                );
            }

            // Step 4: Execute via handler
            ActionResult result = executeWithHandler(action, auth, handler);

            // Step 5: Log result
            context.getAuditLogger().logActionResult(correlationId, result);

            return result;

        } catch (Exception e) {
            logger.error("Error executing action {}: {}", correlationId, e.getMessage(), e);
            ActionResult errorResult = ActionResult.failure(
                    correlationId,
                    "Action execution failed: " + e.getMessage(),
                    Collections.singletonList(e.getMessage())
            );
            context.getAuditLogger().logActionResult(correlationId, errorResult);
            return errorResult;
        }
    }

    /**
     * Executes an action using the appropriate handler based on action type.
     */
    private ActionResult executeWithHandler(Action action, AuthContext auth, ResourceHandler handler) {
        if (action instanceof CreateResourceAction) {
            return handler.create((CreateResourceAction) action, auth);
        } else if (action instanceof ReadResourceAction) {
            return handler.read((ReadResourceAction) action, auth);
        } else if (action instanceof UpdateResourceAction) {
            return handler.update((UpdateResourceAction) action, auth);
        } else if (action instanceof DeleteResourceAction) {
            return handler.delete((DeleteResourceAction) action, auth);
        } else {
            return ActionResult.failure(
                    action.getCorrelationId(),
                    "Unknown action type: " + action.getClass().getSimpleName(),
                    Collections.singletonList("Unsupported action class")
            );
        }
    }

    /**
     * Executes an action asynchronously.
     */
    public CompletableFuture<ActionResult> executeAsync(Action action, AuthContext auth) {
        return CompletableFuture.supplyAsync(
                () -> execute(action, auth),
                context.getExecutorService()
        );
    }

    /**
     * Legacy method for backwards compatibility.
     * Uses an anonymous auth context.
     */
    public ActionResult execute(Action action, String userId) {
        // Create a minimal auth context from userId
        AuthContext auth = AuthContext.builder()
                .userId(userId)
                .permissions(Collections.emptySet())
                .build();
        return execute(action, auth);
    }

    /**
     * Returns the list of supported resource types.
     */
    public java.util.Set<String> getSupportedResourceTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
