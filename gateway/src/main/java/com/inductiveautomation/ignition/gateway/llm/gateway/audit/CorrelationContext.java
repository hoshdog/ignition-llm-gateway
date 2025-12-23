package com.inductiveautomation.ignition.gateway.llm.gateway.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds context information for a correlated set of operations.
 * Used to track related actions and maintain state during multi-step operations.
 * Provides static ThreadLocal access for per-request correlation ID propagation.
 */
public final class CorrelationContext {

    // ThreadLocal for per-request correlation ID propagation
    private static final ThreadLocal<String> CURRENT_CORRELATION_ID = new ThreadLocal<>();

    /**
     * Sets the current thread's correlation ID.
     */
    public static void setCorrelationId(String correlationId) {
        CURRENT_CORRELATION_ID.set(correlationId);
    }

    /**
     * Gets the current thread's correlation ID.
     */
    public static String getCurrentCorrelationId() {
        return CURRENT_CORRELATION_ID.get();
    }

    /**
     * Clears the current thread's correlation context.
     * Should be called in a finally block to prevent thread pool contamination.
     */
    public static void clear() {
        CURRENT_CORRELATION_ID.remove();
    }

    private final String correlationId;
    private final Instant startTime;
    private final String userId;
    private final Map<String, Object> attributes;

    public CorrelationContext(String correlationId, Instant startTime, String userId) {
        this.correlationId = correlationId;
        this.startTime = startTime;
        this.userId = userId;
        this.attributes = new ConcurrentHashMap<>();
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public String getUserId() {
        return userId;
    }

    /**
     * Returns the duration since the context was created.
     */
    public long getDurationMs() {
        return Instant.now().toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Sets a context attribute.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Gets a context attribute.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Gets all context attributes.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public String toString() {
        return "CorrelationContext{" +
                "correlationId='" + correlationId + '\'' +
                ", startTime=" + startTime +
                ", userId='" + userId + '\'' +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
