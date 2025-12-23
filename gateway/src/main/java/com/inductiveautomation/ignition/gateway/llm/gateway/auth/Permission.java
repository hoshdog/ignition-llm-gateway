package com.inductiveautomation.ignition.gateway.llm.gateway.auth;

/**
 * Granular permissions for LLM Gateway operations.
 * Tied to resource types and actions.
 */
public enum Permission {
    // Tag permissions
    TAG_READ("tag:read", "Read tag configuration and values"),
    TAG_CREATE("tag:create", "Create new tags"),
    TAG_UPDATE("tag:update", "Update tag configuration"),
    TAG_DELETE("tag:delete", "Delete tags"),
    TAG_WRITE_VALUE("tag:write_value", "Write tag values (separate from config)"),

    // View permissions
    VIEW_READ("view:read", "Read Perspective view configuration"),
    VIEW_CREATE("view:create", "Create new Perspective views"),
    VIEW_UPDATE("view:update", "Update Perspective views"),
    VIEW_DELETE("view:delete", "Delete Perspective views"),

    // Script permissions
    SCRIPT_READ("script:read", "Read script content"),
    SCRIPT_CREATE("script:create", "Create new scripts"),
    SCRIPT_UPDATE("script:update", "Update scripts"),
    SCRIPT_DELETE("script:delete", "Delete scripts"),
    SCRIPT_EXECUTE("script:execute", "Execute scripts (dangerous)"),

    // Project permissions
    PROJECT_READ("project:read", "Read project configuration"),
    PROJECT_CREATE("project:create", "Create new projects"),
    PROJECT_UPDATE("project:update", "Update project configuration"),
    PROJECT_DELETE("project:delete", "Delete projects"),

    // Named Query permissions
    NAMED_QUERY_READ("named_query:read", "Read named query configuration"),
    NAMED_QUERY_CREATE("named_query:create", "Create named queries"),
    NAMED_QUERY_UPDATE("named_query:update", "Update named queries"),
    NAMED_QUERY_DELETE("named_query:delete", "Delete named queries"),
    NAMED_QUERY_EXECUTE("named_query:execute", "Execute named queries"),

    // Meta permissions
    ADMIN("admin", "Full administrative access"),
    DRY_RUN_ONLY("dry_run_only", "Can only perform dry-run operations"),
    READ_ALL("read_all", "Read access to all resources");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses a permission from its code string.
     */
    public static Permission fromCode(String code) {
        for (Permission p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown permission code: " + code);
    }

    /**
     * Maps a resource type and action to the required permission.
     */
    public static Permission forResourceAction(String resourceType, String action) {
        String permissionCode = resourceType.replace("-", "_") + ":" + action;
        try {
            return fromCode(permissionCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "No permission defined for " + action + " on " + resourceType);
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
