package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

/**
 * Exception thrown when authorization fails.
 */
public class AuthorizationException extends Exception {

    private final Permission requiredPermission;
    private final String resourcePath;

    public AuthorizationException(String message) {
        super(message);
        this.requiredPermission = null;
        this.resourcePath = null;
    }

    public AuthorizationException(String message, Permission requiredPermission, String resourcePath) {
        super(message);
        this.requiredPermission = requiredPermission;
        this.resourcePath = resourcePath;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}
