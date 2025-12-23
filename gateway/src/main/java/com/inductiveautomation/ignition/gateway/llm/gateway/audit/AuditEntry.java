package com.inductiveautomation.ignition.gateway.llm.gateway.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an immutable audit log entry.
 */
public final class AuditEntry {

    private final String id;
    private final String correlationId;
    private final Instant timestamp;
    private final String category;
    private final String eventType;
    private final String userId;
    private final String resourceType;
    private final String resourcePath;
    private final String actionType;
    private final Map<String, Object> details;

    private AuditEntry(Builder builder) {
        this.id = UUID.randomUUID().toString();
        this.correlationId = builder.correlationId;
        this.timestamp = Instant.now();
        this.category = builder.category;
        this.eventType = builder.eventType;
        this.userId = builder.userId;
        this.resourceType = builder.resourceType;
        this.resourcePath = builder.resourcePath;
        this.actionType = builder.actionType;
        this.details = builder.details != null ?
                Collections.unmodifiableMap(builder.details) :
                Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUserId() {
        return userId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getActionType() {
        return actionType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String correlationId;
        private String category;
        private String eventType;
        private String userId;
        private String resourceType;
        private String resourcePath;
        private String actionType;
        private Map<String, Object> details;

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
            return this;
        }

        public Builder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(this);
        }
    }

    @Override
    public String toString() {
        return "AuditEntry{" +
                "id='" + id + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", timestamp=" + timestamp +
                ", category='" + category + '\'' +
                ", eventType='" + eventType + '\'' +
                ", userId='" + userId + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                ", actionType='" + actionType + '\'' +
                '}';
    }
}
