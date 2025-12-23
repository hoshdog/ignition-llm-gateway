package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;

/**
 * Interface for handling CRUD operations on a specific resource type.
 * Each resource type (tags, views, scripts, etc.) implements this interface.
 */
public interface ResourceHandler {

    /**
     * Returns the resource type this handler manages.
     * Examples: "tag", "perspective-view", "script", "named-query"
     */
    String getResourceType();

    /**
     * Creates a new resource.
     */
    ActionResult create(CreateResourceAction action, AuthContext auth);

    /**
     * Reads/retrieves a resource.
     */
    ActionResult read(ReadResourceAction action, AuthContext auth);

    /**
     * Updates an existing resource.
     */
    ActionResult update(UpdateResourceAction action, AuthContext auth);

    /**
     * Deletes a resource.
     */
    ActionResult delete(DeleteResourceAction action, AuthContext auth);

    /**
     * Checks if this handler supports the given action type.
     */
    default boolean supportsAction(String actionType) {
        String lowerAction = actionType.toLowerCase();
        switch (lowerAction) {
            case "create":
            case "read":
            case "update":
            case "delete":
            case "list":
                return true;
            default:
                return false;
        }
    }
}
