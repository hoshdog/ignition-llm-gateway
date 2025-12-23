package com.inductiveautomation.ignition.gateway.llm.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of an action execution.
 * Contains status, data, and any warnings or errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ActionResult {

    public enum Status {
        SUCCESS,
        FAILURE,
        PARTIAL,
        DRY_RUN,
        PENDING_CONFIRMATION
    }

    private final String correlationId;
    private final Status status;
    private final String message;
    private final Object data;
    private final List<String> warnings;
    private final List<String> errors;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final long durationMs;

    @JsonCreator
    public ActionResult(
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status") Status status,
            @JsonProperty("message") String message,
            @JsonProperty("data") Object data,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("errors") List<String> errors,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("durationMs") long durationMs) {
        this.correlationId = correlationId;
        this.status = status;
        this.message = message;
        this.data = data;
        this.warnings = warnings != null ? warnings : Collections.emptyList();
        this.errors = errors != null ? errors : Collections.emptyList();
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.durationMs = durationMs;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.DRY_RUN;
    }

    /**
     * Builder for creating ActionResult instances.
     */
    public static Builder builder(String correlationId) {
        return new Builder(correlationId);
    }

    public static class Builder {
        private final String correlationId;
        private Status status = Status.SUCCESS;
        private String message;
        private Object data;
        private List<String> warnings;
        private List<String> errors;
        private Map<String, Object> metadata;
        private final Instant startTime;

        private Builder(String correlationId) {
            this.correlationId = correlationId;
            this.startTime = Instant.now();
        }

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ActionResult build() {
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            return new ActionResult(
                    correlationId,
                    status,
                    message,
                    data,
                    warnings,
                    errors,
                    metadata,
                    Instant.now(),
                    durationMs
            );
        }
    }

    /**
     * Creates a success result.
     */
    public static ActionResult success(String correlationId, String message, Object data) {
        return builder(correlationId)
                .status(Status.SUCCESS)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Creates a failure result.
     */
    public static ActionResult failure(String correlationId, String message, List<String> errors) {
        return builder(correlationId)
                .status(Status.FAILURE)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Creates a dry run result.
     */
    public static ActionResult dryRun(String correlationId, String message, Object preview) {
        return builder(correlationId)
                .status(Status.DRY_RUN)
                .message(message)
                .data(preview)
                .build();
    }
}
