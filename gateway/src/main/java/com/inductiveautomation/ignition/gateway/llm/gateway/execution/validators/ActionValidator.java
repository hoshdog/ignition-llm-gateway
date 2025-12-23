package com.inductiveautomation.ignition.gateway.llm.gateway.execution.validators;

import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;

import java.util.Set;
import java.util.UUID;

/**
 * Validates action requests before execution.
 * Checks for required fields, valid values, and security constraints.
 */
public class ActionValidator {

    private static final Set<String> VALID_RESOURCE_TYPES = Set.of(
            LLMGatewayConstants.RESOURCE_TYPE_PROJECT,
            LLMGatewayConstants.RESOURCE_TYPE_VIEW,
            LLMGatewayConstants.RESOURCE_TYPE_TAG,
            LLMGatewayConstants.RESOURCE_TYPE_SCRIPT,
            LLMGatewayConstants.RESOURCE_TYPE_NAMED_QUERY,
            LLMGatewayConstants.RESOURCE_TYPE_GATEWAY_CONFIG
    );

    private static final Set<String> VALID_ACTION_TYPES = Set.of(
            LLMGatewayConstants.ACTION_CREATE,
            LLMGatewayConstants.ACTION_READ,
            LLMGatewayConstants.ACTION_UPDATE,
            LLMGatewayConstants.ACTION_DELETE,
            LLMGatewayConstants.ACTION_LIST
    );

    /**
     * Validates an action request.
     */
    public ValidationResult validate(Action action) {
        ValidationResult.Builder result = ValidationResult.builder();

        // Validate correlation ID
        validateCorrelationId(action.getCorrelationId(), result);

        // Validate action type
        validateActionType(action.getActionType(), result);

        // Validate resource type
        validateResourceType(action.getResourceType(), result);

        // Validate resource path
        validateResourcePath(action.getResourcePath(), result);

        // Validate options
        validateOptions(action, result);

        return result.build();
    }

    private void validateCorrelationId(String correlationId, ValidationResult.Builder result) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            result.addError("correlationId", "Correlation ID is required", "REQUIRED_FIELD");
            return;
        }

        // Validate UUID format
        try {
            UUID.fromString(correlationId);
        } catch (IllegalArgumentException e) {
            result.addError("correlationId", "Correlation ID must be a valid UUID", "INVALID_FORMAT");
        }
    }

    private void validateActionType(String actionType, ValidationResult.Builder result) {
        if (actionType == null || actionType.trim().isEmpty()) {
            result.addError("action", "Action type is required", "REQUIRED_FIELD");
            return;
        }

        if (!VALID_ACTION_TYPES.contains(actionType.toLowerCase())) {
            result.addError("action",
                    "Invalid action type. Must be one of: " + VALID_ACTION_TYPES,
                    "INVALID_VALUE");
        }
    }

    private void validateResourceType(String resourceType, ValidationResult.Builder result) {
        if (resourceType == null || resourceType.trim().isEmpty()) {
            result.addError("resourceType", "Resource type is required", "REQUIRED_FIELD");
            return;
        }

        if (!VALID_RESOURCE_TYPES.contains(resourceType.toLowerCase())) {
            result.addError("resourceType",
                    "Invalid resource type. Must be one of: " + VALID_RESOURCE_TYPES,
                    "INVALID_VALUE");
        }
    }

    private void validateResourcePath(String resourcePath, ValidationResult.Builder result) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            result.addError("resourcePath", "Resource path is required", "REQUIRED_FIELD");
            return;
        }

        if (resourcePath.length() > LLMGatewayConstants.MAX_RESOURCE_PATH_LENGTH) {
            result.addError("resourcePath",
                    "Resource path exceeds maximum length of " +
                            LLMGatewayConstants.MAX_RESOURCE_PATH_LENGTH,
                    "MAX_LENGTH_EXCEEDED");
        }

        // Check for path traversal attempts
        if (resourcePath.contains("..") || resourcePath.contains("//")) {
            result.addError("resourcePath",
                    "Resource path contains invalid characters",
                    "SECURITY_VIOLATION");
        }
    }

    private void validateOptions(Action action, ValidationResult.Builder result) {
        if (action.getOptions() != null && action.getOptions().getComment() != null) {
            String comment = action.getOptions().getComment();
            if (comment.length() > LLMGatewayConstants.MAX_COMMENT_LENGTH) {
                result.addError("options.comment",
                        "Comment exceeds maximum length of " +
                                LLMGatewayConstants.MAX_COMMENT_LENGTH,
                        "MAX_LENGTH_EXCEEDED");
            }
        }

        // Warn about force without dry-run on destructive actions
        if (action.isDestructive() && action.getOptions() != null) {
            if (action.getOptions().isForce() && !action.getOptions().isDryRun()) {
                result.addWarning(
                        "Force mode enabled without dry-run for destructive action. " +
                                "Consider using dryRun=true first to preview changes."
                );
            }
        }
    }
}
