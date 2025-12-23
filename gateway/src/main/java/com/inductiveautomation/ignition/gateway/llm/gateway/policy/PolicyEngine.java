package com.inductiveautomation.ignition.gateway.llm.gateway.policy;

import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthorizationException;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Policy engine for authorization and access control decisions.
 * Implements least-privilege defaults with configurable permissions.
 */
public class PolicyEngine {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEngine.class);

    private final EnvironmentMode environmentMode;
    private final Set<String> allowedResourceTypes;
    private final Set<String> allowedActions;
    private final List<PolicyRule> rules;
    private final Map<String, Map<String, Permission>> permissionMap;

    public PolicyEngine(EnvironmentMode environmentMode) {
        this.environmentMode = environmentMode;
        this.allowedResourceTypes = new HashSet<>();
        this.allowedActions = new HashSet<>();
        this.rules = new ArrayList<>();
        this.permissionMap = new HashMap<>();

        initializeDefaultPolicies();
        initializePermissionMap();
    }

    /**
     * Initializes default policies based on environment mode.
     */
    private void initializeDefaultPolicies() {
        // Default allowed resource types
        allowedResourceTypes.add("tag");
        allowedResourceTypes.add("perspective-view");
        allowedResourceTypes.add("project");
        allowedResourceTypes.add("named-query");
        allowedResourceTypes.add("script");

        // Default allowed actions (read is always allowed)
        allowedActions.add("read");
        allowedActions.add("list");

        // Environment-specific permissions
        if (environmentMode == EnvironmentMode.DEVELOPMENT) {
            allowedActions.add("create");
            allowedActions.add("update");
            allowedActions.add("delete");
        } else if (environmentMode == EnvironmentMode.TEST) {
            allowedActions.add("create");
            allowedActions.add("update");
            // Delete requires confirmation in TEST mode
        }
        // PRODUCTION mode: only read/list by default, others require explicit policy

        logger.info("PolicyEngine initialized for {} mode with actions: {}",
                environmentMode, allowedActions);
    }

    /**
     * Initializes the mapping of resource type + action to Permission.
     */
    private void initializePermissionMap() {
        // Tag permissions
        Map<String, Permission> tagPerms = new HashMap<>();
        tagPerms.put("read", Permission.TAG_READ);
        tagPerms.put("list", Permission.TAG_READ);
        tagPerms.put("create", Permission.TAG_CREATE);
        tagPerms.put("update", Permission.TAG_UPDATE);
        tagPerms.put("delete", Permission.TAG_DELETE);
        permissionMap.put("tag", tagPerms);

        // View permissions
        Map<String, Permission> viewPerms = new HashMap<>();
        viewPerms.put("read", Permission.VIEW_READ);
        viewPerms.put("list", Permission.VIEW_READ);
        viewPerms.put("create", Permission.VIEW_CREATE);
        viewPerms.put("update", Permission.VIEW_UPDATE);
        viewPerms.put("delete", Permission.VIEW_DELETE);
        permissionMap.put("perspective-view", viewPerms);
        permissionMap.put("view", viewPerms);

        // Script permissions
        Map<String, Permission> scriptPerms = new HashMap<>();
        scriptPerms.put("read", Permission.SCRIPT_READ);
        scriptPerms.put("list", Permission.SCRIPT_READ);
        scriptPerms.put("create", Permission.SCRIPT_CREATE);
        scriptPerms.put("update", Permission.SCRIPT_UPDATE);
        scriptPerms.put("delete", Permission.SCRIPT_DELETE);
        permissionMap.put("script", scriptPerms);

        // Project permissions
        Map<String, Permission> projectPerms = new HashMap<>();
        projectPerms.put("read", Permission.PROJECT_READ);
        projectPerms.put("list", Permission.PROJECT_READ);
        projectPerms.put("create", Permission.PROJECT_CREATE);
        projectPerms.put("update", Permission.PROJECT_UPDATE);
        projectPerms.put("delete", Permission.PROJECT_DELETE);
        permissionMap.put("project", projectPerms);

        // Named Query permissions
        Map<String, Permission> nqPerms = new HashMap<>();
        nqPerms.put("read", Permission.NAMED_QUERY_READ);
        nqPerms.put("list", Permission.NAMED_QUERY_READ);
        nqPerms.put("create", Permission.NAMED_QUERY_CREATE);
        nqPerms.put("update", Permission.NAMED_QUERY_UPDATE);
        nqPerms.put("delete", Permission.NAMED_QUERY_DELETE);
        permissionMap.put("named-query", nqPerms);
    }

    /**
     * Authorizes an action for an authenticated context.
     * Throws AuthorizationException if not permitted.
     */
    public void authorize(AuthContext auth, Action action) throws AuthorizationException {
        // Admin bypasses all checks
        if (auth.isAdmin()) {
            logger.debug("Admin access granted for {}", action.getActionType());
            return;
        }

        // Check environment restrictions for production
        if (environmentMode == EnvironmentMode.PRODUCTION) {
            enforceProductionRestrictions(auth, action);
        }

        // Check DRY_RUN_ONLY constraint
        if (auth.isDryRunOnly() && !action.getOptions().isDryRun()) {
            throw new AuthorizationException(
                    "This API key can only perform dry-run operations. Set dryRun=true.");
        }

        // Map action to required permission
        Permission required = mapToPermission(action.getResourceType(), action.getActionType());
        if (required == null) {
            throw new AuthorizationException(
                    "No permission mapping for " + action.getActionType() + " on " + action.getResourceType());
        }

        // Check if auth context has required permission
        if (!auth.hasPermission(required)) {
            throw new AuthorizationException(
                    String.format("Missing permission %s for %s on %s",
                            required.getCode(), action.getActionType(), action.getResourceType()),
                    required,
                    action.getResourcePath());
        }

        // Check destructive action requirements in production
        if (action.isDestructive() && environmentMode.requiresDestructiveConfirmation()) {
            if (!action.getOptions().isForce()) {
                throw new AuthorizationException(
                        "Destructive action '" + action.getActionType() +
                                "' requires force=true in " + environmentMode + " mode");
            }
        }

        logger.debug("Authorized {} on {} for {}", action.getActionType(),
                action.getResourceType(), auth.getUserId());
    }

    /**
     * Enforces production-specific restrictions.
     */
    private void enforceProductionRestrictions(AuthContext auth, Action action) throws AuthorizationException {
        // In production, certain actions require explicit permissions
        if (action.isDestructive() && !auth.hasPermission(Permission.ADMIN)) {
            // Check for specific delete permission
            Permission deletePermission = mapToPermission(action.getResourceType(), "delete");
            if (deletePermission != null && !auth.hasPermission(deletePermission)) {
                throw new AuthorizationException(
                        "Destructive actions require explicit permission in production mode");
            }
        }
    }

    /**
     * Maps a resource type and action to the required permission.
     */
    public Permission mapToPermission(String resourceType, String actionType) {
        Map<String, Permission> resourcePerms = permissionMap.get(resourceType.toLowerCase());
        if (resourcePerms != null) {
            return resourcePerms.get(actionType.toLowerCase());
        }
        return null;
    }

    /**
     * Evaluates whether an action is permitted (legacy method for backwards compatibility).
     */
    public PolicyDecision evaluate(Action action, String userId) {
        String actionType = action.getActionType();
        String resourceType = action.getResourceType();

        // Check if action type is allowed
        if (!allowedActions.contains(actionType)) {
            return PolicyDecision.deny(
                    "Action type '" + actionType + "' is not permitted in " + environmentMode + " mode"
            );
        }

        // Check if resource type is allowed
        if (!allowedResourceTypes.contains(resourceType)) {
            return PolicyDecision.deny(
                    "Resource type '" + resourceType + "' is not permitted"
            );
        }

        // Check destructive action requirements
        if (action.isDestructive() && environmentMode.requiresDestructiveConfirmation()) {
            if (!action.getOptions().isForce()) {
                return PolicyDecision.requireConfirmation(
                        "Destructive action '" + actionType + "' requires confirmation in " +
                                environmentMode + " mode. Use force=true to proceed."
                );
            }
        }

        // Evaluate custom rules
        for (PolicyRule rule : rules) {
            PolicyDecision decision = rule.evaluate(action, userId, environmentMode);
            if (!decision.isAllowed()) {
                return decision;
            }
        }

        return PolicyDecision.allow();
    }

    /**
     * Adds a custom policy rule.
     */
    public void addRule(PolicyRule rule) {
        rules.add(rule);
        logger.debug("Added policy rule: {}", rule.getName());
    }

    /**
     * Adds an allowed resource type.
     */
    public void allowResourceType(String resourceType) {
        allowedResourceTypes.add(resourceType);
    }

    /**
     * Adds an allowed action.
     */
    public void allowAction(String action) {
        allowedActions.add(action);
    }

    /**
     * Returns the current environment mode.
     */
    public EnvironmentMode getEnvironmentMode() {
        return environmentMode;
    }

    /**
     * Represents a policy decision result.
     */
    public static class PolicyDecision {
        private final boolean allowed;
        private final boolean requiresConfirmation;
        private final String reason;

        private PolicyDecision(boolean allowed, boolean requiresConfirmation, String reason) {
            this.allowed = allowed;
            this.requiresConfirmation = requiresConfirmation;
            this.reason = reason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean requiresConfirmation() {
            return requiresConfirmation;
        }

        public String getReason() {
            return reason;
        }

        public static PolicyDecision allow() {
            return new PolicyDecision(true, false, null);
        }

        public static PolicyDecision deny(String reason) {
            return new PolicyDecision(false, false, reason);
        }

        public static PolicyDecision requireConfirmation(String reason) {
            return new PolicyDecision(false, true, reason);
        }
    }

    /**
     * Interface for custom policy rules.
     */
    public interface PolicyRule {
        String getName();
        PolicyDecision evaluate(Action action, String userId, EnvironmentMode mode);
    }
}
