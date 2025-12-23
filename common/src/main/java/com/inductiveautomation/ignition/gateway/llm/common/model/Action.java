package com.inductiveautomation.ignition.gateway.llm.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;

/**
 * Base interface for all LLM-initiated actions.
 * Actions are typed operations that can be validated, audited, and executed.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "action"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateResourceAction.class, name = "create"),
        @JsonSubTypes.Type(value = ReadResourceAction.class, name = "read"),
        @JsonSubTypes.Type(value = UpdateResourceAction.class, name = "update"),
        @JsonSubTypes.Type(value = DeleteResourceAction.class, name = "delete")
})
public interface Action {

    /**
     * Returns the unique correlation ID for this action request.
     * Used for tracing and audit logging.
     */
    String getCorrelationId();

    /**
     * Returns the action type (create, read, update, delete, list).
     */
    String getActionType();

    /**
     * Returns the type of resource this action targets.
     */
    String getResourceType();

    /**
     * Returns the path to the target resource.
     */
    String getResourcePath();

    /**
     * Returns the action options (dryRun, force, comment, etc.).
     */
    ActionOptions getOptions();

    /**
     * Returns whether this action is destructive (delete or overwrite).
     */
    default boolean isDestructive() {
        return false;
    }

    /**
     * Returns whether this action requires confirmation in production.
     */
    default boolean requiresConfirmation() {
        return isDestructive();
    }
}
