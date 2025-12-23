package com.inductiveautomation.ignition.gateway.llm.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;

import java.util.List;

/**
 * Action to read/retrieve an Ignition resource.
 */
public final class ReadResourceAction extends AbstractAction {

    private final List<String> fields;
    private final boolean includeChildren;
    private final Integer depth;

    @JsonCreator
    public ReadResourceAction(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("fields") List<String> fields,
            @JsonProperty("includeChildren") Boolean includeChildren,
            @JsonProperty("depth") Integer depth,
            @JsonProperty("options") ActionOptions options) {
        super(correlationId, resourceType, resourcePath, options);
        this.fields = fields;
        this.includeChildren = includeChildren != null ? includeChildren : false;
        this.depth = depth;
    }

    @Override
    public String getActionType() {
        return LLMGatewayConstants.ACTION_READ;
    }

    /**
     * Returns the specific fields to retrieve.
     * If null or empty, all fields are returned.
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * Returns whether to include child resources in the response.
     */
    public boolean isIncludeChildren() {
        return includeChildren;
    }

    /**
     * Returns the maximum depth for child resource traversal.
     * Null means no limit (use with caution).
     */
    public Integer getDepth() {
        return depth;
    }

    @Override
    public boolean isDestructive() {
        return false; // Read operations are never destructive
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Read operations don't need confirmation
    }
}
