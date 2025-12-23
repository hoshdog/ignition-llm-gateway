package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import java.util.Objects;

/**
 * Represents a path to a Named Query resource in an Ignition project.
 * Parses paths like "ProjectName/Queries/GetAlarms" or "ProjectName/DataQueries/Users/GetByEmail".
 */
public final class NamedQueryPath {

    private final String projectName;
    private final String folder;
    private final String queryName;

    private NamedQueryPath(String projectName, String folder, String queryName) {
        this.projectName = Objects.requireNonNull(projectName, "projectName cannot be null");
        this.folder = folder != null ? folder : "";
        this.queryName = Objects.requireNonNull(queryName, "queryName cannot be null");
    }

    /**
     * Parses a full path like "ProjectName/path/to/query".
     * @param fullPath The full path to parse
     * @return A NamedQueryPath instance
     * @throws IllegalArgumentException if the path format is invalid
     */
    public static NamedQueryPath parse(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Named query path cannot be null or empty");
        }

        int firstSlash = fullPath.indexOf('/');
        if (firstSlash == -1) {
            throw new IllegalArgumentException(
                    "Invalid named query path. Expected: 'ProjectName/path/to/query', got: " + fullPath);
        }

        String projectName = fullPath.substring(0, firstSlash);
        String remainingPath = fullPath.substring(firstSlash + 1);

        if (remainingPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid named query path. Query name is required after project name.");
        }

        int lastSlash = remainingPath.lastIndexOf('/');
        String folder;
        String name;

        if (lastSlash == -1) {
            // No folder, just query name
            folder = "";
            name = remainingPath;
        } else {
            folder = remainingPath.substring(0, lastSlash);
            name = remainingPath.substring(lastSlash + 1);
        }

        return new NamedQueryPath(projectName, folder, name);
    }

    /**
     * Creates a NamedQueryPath from individual components.
     */
    public static NamedQueryPath of(String projectName, String folder, String queryName) {
        return new NamedQueryPath(projectName, folder, queryName);
    }

    public String getProjectName() {
        return projectName;
    }

    public String getFolder() {
        return folder;
    }

    public String getQueryName() {
        return queryName;
    }

    /**
     * Returns the full path as a string.
     */
    public String toFullPath() {
        StringBuilder sb = new StringBuilder();
        sb.append(projectName);
        sb.append("/");
        if (!folder.isEmpty()) {
            sb.append(folder);
            sb.append("/");
        }
        sb.append(queryName);
        return sb.toString();
    }

    /**
     * Returns the resource path within the project (without project name).
     */
    public String toResourcePath() {
        if (folder.isEmpty()) {
            return "named-query/" + queryName;
        }
        return "named-query/" + folder + "/" + queryName;
    }

    /**
     * Returns the query path within the project (excluding project name).
     */
    public String getQueryPath() {
        if (folder.isEmpty()) {
            return queryName;
        }
        return folder + "/" + queryName;
    }

    /**
     * Returns the folder path for listing queries.
     */
    public String getParentPath() {
        return folder.isEmpty() ? "named-query" : "named-query/" + folder;
    }

    @Override
    public String toString() {
        return toFullPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamedQueryPath that = (NamedQueryPath) o;
        return Objects.equals(projectName, that.projectName) &&
                Objects.equals(folder, that.folder) &&
                Objects.equals(queryName, that.queryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, folder, queryName);
    }
}
