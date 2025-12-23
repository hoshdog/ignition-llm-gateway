package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

/**
 * Exception thrown when authentication fails.
 */
public class AuthenticationException extends Exception {

    private final AuthFailureReason reason;

    public AuthenticationException(String message, AuthFailureReason reason) {
        super(message);
        this.reason = reason;
    }

    public AuthenticationException(String message, AuthFailureReason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AuthFailureReason getReason() {
        return reason;
    }

    /**
     * Reasons for authentication failure.
     */
    public enum AuthFailureReason {
        MISSING_CREDENTIALS("No credentials provided"),
        INVALID_CREDENTIALS("Invalid API key or token"),
        EXPIRED_CREDENTIALS("Credentials have expired"),
        DISABLED_KEY("API key has been disabled"),
        MALFORMED_HEADER("Malformed Authorization header"),
        RATE_LIMITED("Too many authentication attempts");

        private final String description;

        AuthFailureReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
