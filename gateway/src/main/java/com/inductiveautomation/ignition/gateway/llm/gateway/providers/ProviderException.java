package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

/**
 * Exception thrown by LLM providers during API operations.
 */
public class ProviderException extends Exception {

    private final String providerId;
    private final int statusCode;
    private final boolean retryable;

    public ProviderException(String message) {
        super(message);
        this.providerId = null;
        this.statusCode = -1;
        this.retryable = false;
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
        this.providerId = null;
        this.statusCode = -1;
        this.retryable = false;
    }

    public ProviderException(String providerId, String message, int statusCode, boolean retryable) {
        super(message);
        this.providerId = providerId;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public ProviderException(String providerId, String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public String getProviderId() {
        return providerId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Factory for rate limit errors
     */
    public static ProviderException rateLimited(String providerId, String message) {
        return new ProviderException(providerId, message, 429, true);
    }

    /**
     * Factory for authentication errors
     */
    public static ProviderException unauthorized(String providerId, String message) {
        return new ProviderException(providerId, message, 401, false);
    }

    /**
     * Factory for server errors
     */
    public static ProviderException serverError(String providerId, String message) {
        return new ProviderException(providerId, message, 500, true);
    }
}
