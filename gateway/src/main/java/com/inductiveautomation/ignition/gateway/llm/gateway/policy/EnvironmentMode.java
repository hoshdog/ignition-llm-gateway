package com.inductiveautomation.ignition.gateway.llm.gateway.policy;

import com.inductiveautomation.ignition.gateway.llm.common.LLMGatewayConstants;

/**
 * Represents the deployment environment mode.
 * Different modes have different security and confirmation policies.
 */
public enum EnvironmentMode {

    /**
     * Development mode - most permissive, suitable for local development.
     */
    DEVELOPMENT(LLMGatewayConstants.ENV_MODE_DEV, false, false),

    /**
     * Test mode - moderate restrictions, suitable for staging/QA.
     */
    TEST(LLMGatewayConstants.ENV_MODE_TEST, true, false),

    /**
     * Production mode - strictest policies, requires confirmations.
     */
    PRODUCTION(LLMGatewayConstants.ENV_MODE_PROD, true, true);

    private final String value;
    private final boolean requiresAuditLog;
    private final boolean requiresDestructiveConfirmation;

    EnvironmentMode(String value, boolean requiresAuditLog, boolean requiresDestructiveConfirmation) {
        this.value = value;
        this.requiresAuditLog = requiresAuditLog;
        this.requiresDestructiveConfirmation = requiresDestructiveConfirmation;
    }

    public String getValue() {
        return value;
    }

    /**
     * Returns whether audit logging is required in this mode.
     */
    public boolean requiresAuditLog() {
        return requiresAuditLog;
    }

    /**
     * Returns whether destructive operations require explicit confirmation.
     */
    public boolean requiresDestructiveConfirmation() {
        return requiresDestructiveConfirmation;
    }

    /**
     * Returns whether dry-run mode is enforced for all operations.
     */
    public boolean enforceDryRun() {
        return false; // Could be enabled for specific environments
    }

    /**
     * Parses an environment mode from a string value.
     */
    public static EnvironmentMode fromString(String value) {
        for (EnvironmentMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown environment mode: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
