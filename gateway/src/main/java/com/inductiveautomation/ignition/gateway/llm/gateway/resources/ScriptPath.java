package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import java.util.Objects;

/**
 * Represents a path to a script resource in an Ignition project.
 * Parses paths like "ProjectName/library/utils/helpers" or "ProjectName/gateway/timer/myTimer".
 */
public final class ScriptPath {

    private final String projectName;
    private final String folder;
    private final String scriptName;
    private final ScriptType type;

    private ScriptPath(String projectName, String folder, String scriptName, ScriptType type) {
        this.projectName = Objects.requireNonNull(projectName, "projectName cannot be null");
        this.folder = folder != null ? folder : "";
        this.scriptName = Objects.requireNonNull(scriptName, "scriptName cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Parses a full path like "ProjectName/type/path/to/script".
     * @param fullPath The full path to parse
     * @return A ScriptPath instance
     * @throws IllegalArgumentException if the path format is invalid
     */
    public static ScriptPath parse(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Script path cannot be null or empty");
        }

        String[] parts = fullPath.split("/", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid script path. Expected: 'ProjectName/type/path/to/script', got: " + fullPath);
        }

        String projectName = parts[0];
        String typeStr = parts[1];
        String scriptPath = parts[2];

        ScriptType type = parseScriptType(typeStr);

        int lastSlash = scriptPath.lastIndexOf('/');
        String folder;
        String name;
        if (lastSlash > 0) {
            folder = scriptPath.substring(0, lastSlash);
            name = scriptPath.substring(lastSlash + 1);
        } else {
            folder = "";
            name = scriptPath;
        }

        return new ScriptPath(projectName, folder, name, type);
    }

    /**
     * Creates a ScriptPath from individual components.
     */
    public static ScriptPath of(String projectName, ScriptType type, String folder, String scriptName) {
        return new ScriptPath(projectName, folder, scriptName, type);
    }

    /**
     * Parses the script type from a string.
     */
    private static ScriptType parseScriptType(String typeStr) {
        String lowerType = typeStr.toLowerCase();
        switch (lowerType) {
            case "library":
                return ScriptType.PROJECT_LIBRARY;
            case "gateway":
                return ScriptType.GATEWAY_EVENT;
            case "tag":
                return ScriptType.TAG_EVENT;
            case "perspective":
                return ScriptType.PERSPECTIVE;
            case "message":
                return ScriptType.MESSAGE_HANDLER;
            default:
                throw new IllegalArgumentException("Unknown script type: " + typeStr +
                        ". Valid types: library, gateway, tag, perspective, message");
        }
    }

    public String getProjectName() {
        return projectName;
    }

    public String getFolder() {
        return folder;
    }

    public String getScriptName() {
        return scriptName;
    }

    public ScriptType getType() {
        return type;
    }

    /**
     * Returns the full path as a string.
     */
    public String toFullPath() {
        StringBuilder sb = new StringBuilder();
        sb.append(projectName);
        sb.append("/");
        sb.append(type.getPathPrefix());
        sb.append("/");
        if (!folder.isEmpty()) {
            sb.append(folder);
            sb.append("/");
        }
        sb.append(scriptName);
        return sb.toString();
    }

    /**
     * Returns the resource path within the project (without project name).
     */
    public String toResourcePath() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getResourceFolder());
        if (!folder.isEmpty()) {
            sb.append("/");
            sb.append(folder);
        }
        sb.append("/");
        sb.append(scriptName);
        return sb.toString();
    }

    /**
     * Returns the folder path for listing scripts.
     */
    public String getParentPath() {
        if (folder.isEmpty()) {
            return type.getResourceFolder();
        }
        return type.getResourceFolder() + "/" + folder;
    }

    @Override
    public String toString() {
        return toFullPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptPath that = (ScriptPath) o;
        return Objects.equals(projectName, that.projectName) &&
                Objects.equals(folder, that.folder) &&
                Objects.equals(scriptName, that.scriptName) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, folder, scriptName, type);
    }

    /**
     * Enumeration of script types in Ignition.
     */
    public enum ScriptType {
        PROJECT_LIBRARY("library", "script-python/resource"),
        GATEWAY_EVENT("gateway", "gateway-event-scripts"),
        TAG_EVENT("tag", "tag-scripts"),
        PERSPECTIVE("perspective", "com.inductiveautomation.perspective/session-scripts"),
        MESSAGE_HANDLER("message", "message-handlers");

        private final String pathPrefix;
        private final String resourceFolder;

        ScriptType(String pathPrefix, String resourceFolder) {
            this.pathPrefix = pathPrefix;
            this.resourceFolder = resourceFolder;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public String getResourceFolder() {
            return resourceFolder;
        }
    }
}
