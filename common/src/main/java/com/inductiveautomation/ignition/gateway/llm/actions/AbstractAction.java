package com.inductiveautomation.ignition.gateway.llm.actions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;

import java.util.Objects;

/**
 * Base implementation for all actions with common fields.
 */
public abstract class AbstractAction implements Action {

    protected final String correlationId;
    protected final String resourceType;
    protected final String resourcePath;
    protected final ActionOptions options;

    protected AbstractAction(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("options") ActionOptions options) {
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId cannot be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        this.options = options != null ? options : ActionOptions.defaults();
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public ActionOptions getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "correlationId='" + correlationId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                ", options=" + options +
                '}';
    }
}
