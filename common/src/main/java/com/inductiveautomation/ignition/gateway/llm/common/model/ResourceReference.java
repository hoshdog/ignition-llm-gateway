package com.inductiveautomation.ignition.gateway.llm.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a reference to an Ignition resource.
 * Uses named paths rather than internal IDs for stability across environments.
 */
public final class ResourceReference {

    private final String resourceType;
    private final String resourcePath;
    private final String projectName;

    @JsonCreator
    public ResourceReference(
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("projectName") String projectName) {
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath cannot be null");
        this.projectName = projectName; // Can be null for global resources
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getProjectName() {
        return projectName;
    }

    /**
     * Returns a fully qualified path including project name if applicable.
     */
    public String getFullPath() {
        if (projectName != null && !projectName.isEmpty()) {
            return projectName + "/" + resourcePath;
        }
        return resourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceReference that = (ResourceReference) o;
        return Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourcePath, that.resourcePath) &&
                Objects.equals(projectName, that.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, resourcePath, projectName);
    }

    @Override
    public String toString() {
        return "ResourceReference{" +
                "resourceType='" + resourceType + '\'' +
                ", resourcePath='" + resourcePath + '\'' +
                ", projectName='" + projectName + '\'' +
                '}';
    }

    /**
     * Creates a resource reference for a project-scoped resource.
     */
    public static ResourceReference forProject(String resourceType, String projectName, String resourcePath) {
        return new ResourceReference(resourceType, resourcePath, projectName);
    }

    /**
     * Creates a resource reference for a global (non-project) resource.
     */
    public static ResourceReference forGlobal(String resourceType, String resourcePath) {
        return new ResourceReference(resourceType, resourcePath, null);
    }
}
