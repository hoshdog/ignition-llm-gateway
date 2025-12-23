package com.inductiveautomation.ignition.gateway.llm.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;

/**
 * Action to delete an Ignition resource.
 */
public final class DeleteResourceAction extends AbstractAction {

    private final boolean recursive;

    @JsonCreator
    public DeleteResourceAction(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("recursive") Boolean recursive,
            @JsonProperty("options") ActionOptions options) {
        super(correlationId, resourceType, resourcePath, options);
        this.recursive = recursive != null ? recursive : false;
    }

    @Override
    public String getActionType() {
        return LLMGatewayConstants.ACTION_DELETE;
    }

    /**
     * Returns whether to recursively delete child resources.
     */
    public boolean isRecursive() {
        return recursive;
    }

    @Override
    public boolean isDestructive() {
        return true; // Delete is always destructive
    }

    @Override
    public boolean requiresConfirmation() {
        // All delete operations require confirmation unless forced
        return !getOptions().isForce();
    }
}
