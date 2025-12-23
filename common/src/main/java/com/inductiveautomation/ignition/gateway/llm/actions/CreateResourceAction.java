package com.inductiveautomation.ignition.gateway.llm.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;

import java.util.Map;

/**
 * Action to create a new Ignition resource.
 */
public final class CreateResourceAction extends AbstractAction {

    private final Map<String, Object> payload;

    @JsonCreator
    public CreateResourceAction(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("options") ActionOptions options) {
        super(correlationId, resourceType, resourcePath, options);
        this.payload = payload;
    }

    @Override
    public String getActionType() {
        return LLMGatewayConstants.ACTION_CREATE;
    }

    /**
     * Returns the payload containing the resource data to create.
     */
    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public boolean isDestructive() {
        return false; // Create is not destructive (doesn't overwrite existing)
    }

    @Override
    public boolean requiresConfirmation() {
        // Creating resources typically doesn't need confirmation
        // unless it would affect system stability
        return false;
    }
}
