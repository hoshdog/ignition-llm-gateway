package com.inductiveautomation.ignition.gateway.llm.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;

import java.util.Map;

/**
 * Action to update an existing Ignition resource.
 */
public final class UpdateResourceAction extends AbstractAction {

    private final Map<String, Object> payload;
    private final boolean merge;

    @JsonCreator
    public UpdateResourceAction(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("merge") Boolean merge,
            @JsonProperty("options") ActionOptions options) {
        super(correlationId, resourceType, resourcePath, options);
        this.payload = payload;
        this.merge = merge != null ? merge : true; // Default to merge mode
    }

    @Override
    public String getActionType() {
        return LLMGatewayConstants.ACTION_UPDATE;
    }

    /**
     * Returns the payload containing the fields to update.
     */
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Returns whether to merge the payload with existing data (patch)
     * or replace the entire resource.
     */
    public boolean isMerge() {
        return merge;
    }

    @Override
    public boolean isDestructive() {
        // Updates can be destructive if replacing entire resource
        return !merge;
    }

    @Override
    public boolean requiresConfirmation() {
        // Full replacement (non-merge) requires confirmation
        // Merge updates are generally safer
        return !merge;
    }
}
