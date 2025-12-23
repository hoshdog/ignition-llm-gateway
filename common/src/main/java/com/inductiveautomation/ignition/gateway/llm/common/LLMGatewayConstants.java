package com.inductiveautomation.ignition.gateway.llm.common;

/**
 * Constants used throughout the LLM Gateway module.
 */
public final class LLMGatewayConstants {

    private LLMGatewayConstants() {
        // Prevent instantiation
    }

    // Module Identification
    public static final String MODULE_ID = "com.inductiveautomation.ignition.gateway.llm";
    public static final String MODULE_NAME = "LLM Gateway";
    public static final String MODULE_VERSION = "1.0.0";

    // API Configuration
    public static final String API_VERSION = "v1";
    public static final String API_BASE_PATH = "/data/llm-gateway/" + API_VERSION;
    public static final String HEALTH_CHECK_PATH = API_BASE_PATH + "/health";
    public static final String ACTIONS_PATH = API_BASE_PATH + "/actions";

    // Resource Types
    public static final String RESOURCE_TYPE_PROJECT = "project";
    public static final String RESOURCE_TYPE_VIEW = "perspective-view";
    public static final String RESOURCE_TYPE_TAG = "tag";
    public static final String RESOURCE_TYPE_SCRIPT = "script";
    public static final String RESOURCE_TYPE_NAMED_QUERY = "named-query";
    public static final String RESOURCE_TYPE_GATEWAY_CONFIG = "gateway-config";

    // Action Types
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_READ = "read";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_LIST = "list";

    // Environment Modes
    public static final String ENV_MODE_DEV = "development";
    public static final String ENV_MODE_TEST = "test";
    public static final String ENV_MODE_PROD = "production";

    // Audit Log Categories
    public static final String AUDIT_CATEGORY_ACTION = "LLM_ACTION";
    public static final String AUDIT_CATEGORY_AUTH = "LLM_AUTH";
    public static final String AUDIT_CATEGORY_POLICY = "LLM_POLICY";
    public static final String AUDIT_CATEGORY_SYSTEM = "LLM_SYSTEM";

    // Default Timeouts (milliseconds)
    public static final long DEFAULT_ACTION_TIMEOUT_MS = 30000;
    public static final long DEFAULT_LLM_REQUEST_TIMEOUT_MS = 60000;

    // Validation Limits
    public static final int MAX_RESOURCE_PATH_LENGTH = 500;
    public static final int MAX_PAYLOAD_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final int MAX_COMMENT_LENGTH = 1000;
}
