package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Resource handler for Ignition Projects.
 * Provides read/list operations with restricted create/delete for safety.
 *
 * Projects are primarily read-only from LLM perspective since creation/deletion
 * should generally be done through Designer or Gateway web interface.
 */
public class ProjectResourceHandler implements ResourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProjectResourceHandler.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    // Fields that can be updated via LLM
    private static final Set<String> ALLOWED_UPDATE_FIELDS = new HashSet<>(Arrays.asList(
            "title",
            "description",
            "enabled"
    ));

    public ProjectResourceHandler(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getResourceType() {
        return "project";
    }

    @Override
    public ActionResult create(CreateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();

        // Project creation is restricted - usually done through Designer
        if (!auth.hasPermission(Permission.ADMIN)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Project creation requires ADMIN permission. " +
                            "Consider creating projects through Ignition Designer instead.")
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        // Even with ADMIN permission, we don't support project creation via LLM
        // for safety reasons
        return ActionResult.builder(correlationId)
                .status(ActionResult.Status.FAILURE)
                .message("Project creation via LLM is not supported for safety reasons. " +
                        "Please create projects through Ignition Designer or Gateway web interface.")
                .errors(Collections.singletonList("NOT_IMPLEMENTED"))
                .build();
    }

    @Override
    public ActionResult read(ReadResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Reading project: {} (correlationId={})", resourcePath, correlationId);

        // List all projects
        if (resourcePath.equals("*") || resourcePath.isEmpty()) {
            return listProjects(correlationId, action);
        }

        // Get specific project details
        String projectName = resourcePath;

        try {
            if (!projectExists(projectName)) {
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Project not found: " + projectName)
                        .errors(Collections.singletonList("NOT_FOUND"))
                        .build();
            }

            Map<String, Object> projectDetails = readProjectInternal(projectName);

            return ActionResult.success(correlationId,
                    "Project read successfully",
                    projectDetails);

        } catch (Exception e) {
            logger.error("Failed to read project: {}", projectName, e);
            return ActionResult.failure(correlationId,
                    "Failed to read project: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult update(UpdateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String projectName = action.getResourcePath();

        logger.debug("Updating project: {} (correlationId={})", projectName, correlationId);

        // Check permissions
        if (!auth.hasPermission(Permission.PROJECT_UPDATE) && !auth.hasPermission(Permission.ADMIN)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Missing PROJECT_UPDATE permission")
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        if (!projectExists(projectName)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Project not found: " + projectName)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        Map<String, Object> payload = action.getPayload();
        if (payload == null || payload.isEmpty()) {
            return ActionResult.failure(correlationId,
                    "Update payload is required",
                    Collections.singletonList("payload is empty"));
        }

        // Only allow safe metadata updates
        List<String> disallowedFields = new ArrayList<>();
        for (String key : payload.keySet()) {
            if (!ALLOWED_UPDATE_FIELDS.contains(key) && !key.startsWith("_")) {
                disallowedFields.add(key);
            }
        }

        if (!disallowedFields.isEmpty()) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Cannot update fields: " + disallowedFields +
                            ". Only allowed: " + ALLOWED_UPDATE_FIELDS)
                    .errors(Collections.singletonList("FORBIDDEN_FIELDS"))
                    .build();
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("projectName", projectName);
            preview.put("changes", payload);

            return ActionResult.dryRun(correlationId,
                    "Would update project: " + projectName,
                    preview);
        }

        try {
            Map<String, Object> result = updateProjectInternal(projectName, payload);

            String comment = action.getOptions().getComment();
            auditLogger.logResourceUpdated("project", projectName, auth, comment);
            logger.info("Updated project: {} by user {}", projectName, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Project updated successfully: " + projectName,
                    result);

        } catch (Exception e) {
            logger.error("Failed to update project: {}", projectName, e);
            return ActionResult.failure(correlationId,
                    "Failed to update project: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult delete(DeleteResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String projectName = action.getResourcePath();

        logger.debug("Delete project requested: {} (correlationId={})", projectName, correlationId);

        // Project deletion is dangerous - require ADMIN
        if (!auth.hasPermission(Permission.ADMIN)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Project deletion requires ADMIN permission")
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        if (!projectExists(projectName)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Project not found: " + projectName)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        // Require force=true
        if (!action.getOptions().isForce()) {
            Map<String, Object> confirmData = new LinkedHashMap<>();
            confirmData.put("projectName", projectName);
            confirmData.put("resourceCounts", getProjectResourceCounts(projectName));

            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.PENDING_CONFIRMATION)
                    .message("WARNING: Deleting project '" + projectName +
                            "' will permanently remove ALL resources (views, scripts, queries). " +
                            "This action CANNOT be undone. Set force=true to confirm.")
                    .data(confirmData)
                    .warnings(Collections.singletonList(
                            "This will permanently delete all project resources!"))
                    .build();
        }

        // Even with force, we don't support project deletion via LLM for safety
        return ActionResult.builder(correlationId)
                .status(ActionResult.Status.FAILURE)
                .message("Project deletion via LLM is disabled for safety. " +
                        "Please delete projects through Ignition Designer or Gateway web interface.")
                .errors(Collections.singletonList("NOT_IMPLEMENTED"))
                .build();
    }

    // ===== Internal helper methods =====

    /**
     * Gets the path to the projects directory.
     */
    private Path getProjectsDirectory() {
        Path dataDir = gatewayContext.getSystemManager().getDataDir().toPath();
        return dataDir.resolve("projects");
    }

    /**
     * Gets the path to a specific project directory.
     */
    private Path getProjectDirectory(String projectName) {
        return getProjectsDirectory().resolve(projectName);
    }

    /**
     * Checks if a project exists on the filesystem.
     */
    private boolean projectExists(String projectName) {
        Path projectDir = getProjectDirectory(projectName);
        Path projectJson = projectDir.resolve("project.json");
        boolean exists = Files.exists(projectDir) && Files.isDirectory(projectDir) && Files.exists(projectJson);
        logger.debug("Checking if project exists: {} -> {}", projectName, exists);
        return exists;
    }

    /**
     * Reads a project's metadata from project.json.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readProjectInternal(String projectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", projectName);

        Path projectDir = getProjectDirectory(projectName);
        Path projectJson = projectDir.resolve("project.json");

        try {
            if (Files.exists(projectJson)) {
                String content = Files.readString(projectJson, StandardCharsets.UTF_8);
                Map<String, Object> projectMeta = objectMapper.readValue(content, Map.class);

                result.put("title", projectMeta.getOrDefault("title", projectName));
                result.put("description", projectMeta.getOrDefault("description", ""));
                result.put("enabled", projectMeta.getOrDefault("enabled", true));
                result.put("inheritable", projectMeta.getOrDefault("inheritable", false));
                result.put("parentProject", projectMeta.get("parent"));
            } else {
                // Fallback if project.json not readable
                result.put("title", projectName);
                result.put("description", "");
                result.put("enabled", true);
                result.put("inheritable", false);
                result.put("parentProject", null);
            }
        } catch (Exception e) {
            logger.warn("Failed to read project.json for {}: {}", projectName, e.getMessage());
            result.put("title", projectName);
            result.put("description", "");
            result.put("enabled", true);
            result.put("inheritable", false);
            result.put("parentProject", null);
        }

        // Add resource counts
        result.put("resourceCounts", getProjectResourceCounts(projectName));

        return result;
    }

    private Map<String, Object> updateProjectInternal(String projectName,
                                                       Map<String, Object> updates) {
        // Placeholder implementation - actual update would modify project.json
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectName", projectName);
        result.put("status", "updated");

        // Echo back the updates
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    private ActionResult listProjects(String correlationId, ReadResourceAction action) {
        logger.debug("Listing all projects");

        try {
            boolean includeDisabled = false;
            if (action.getFields() != null &&
                    action.getFields().contains("includeDisabled")) {
                includeDisabled = true;
            }

            List<Map<String, Object>> projects = listProjectsInternal(includeDisabled);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("projects", projects);
            result.put("count", projects.size());

            return ActionResult.success(correlationId, "Projects listed successfully", result);

        } catch (Exception e) {
            logger.error("Failed to list projects", e);
            return ActionResult.failure(correlationId,
                    "Failed to list projects: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Lists all projects by scanning the projects directory on disk.
     * Returns actual project names (folder names) with metadata from project.json.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listProjectsInternal(boolean includeDisabled) {
        List<Map<String, Object>> projects = new ArrayList<>();

        try {
            Path projectsDir = getProjectsDirectory();

            if (!Files.exists(projectsDir)) {
                logger.warn("Projects directory not found: {}", projectsDir);
                return projects;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectsDir)) {
                for (Path projectDir : stream) {
                    if (!Files.isDirectory(projectDir)) {
                        continue;
                    }

                    String projectName = projectDir.getFileName().toString();
                    Path projectJson = projectDir.resolve("project.json");

                    if (!Files.exists(projectJson)) {
                        logger.debug("Skipping directory without project.json: {}", projectName);
                        continue;
                    }

                    try {
                        String content = Files.readString(projectJson, StandardCharsets.UTF_8);
                        Map<String, Object> projectMeta = objectMapper.readValue(content, Map.class);

                        boolean enabled = Boolean.TRUE.equals(projectMeta.getOrDefault("enabled", true));
                        if (!includeDisabled && !enabled) {
                            logger.debug("Skipping disabled project: {}", projectName);
                            continue;
                        }

                        Map<String, Object> projectInfo = new LinkedHashMap<>();
                        projectInfo.put("name", projectName);  // Folder name is the identifier
                        projectInfo.put("title", projectMeta.getOrDefault("title", projectName));
                        projectInfo.put("enabled", enabled);
                        projectInfo.put("parent", projectMeta.get("parent"));

                        projects.add(projectInfo);

                        logger.debug("Found project: {} (title: {})", projectName, projectMeta.get("title"));

                    } catch (Exception e) {
                        logger.warn("Failed to read project.json for {}: {}", projectName, e.getMessage());
                    }
                }
            }

            logger.info("Listed {} projects from filesystem", projects.size());

        } catch (Exception e) {
            logger.error("Failed to list projects from filesystem", e);
        }

        return projects;
    }

    /**
     * Counts resources within a project by scanning the filesystem.
     */
    private Map<String, Integer> getProjectResourceCounts(String projectName) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        try {
            Path projectDir = getProjectDirectory(projectName);

            if (!Files.exists(projectDir)) {
                counts.put("views", 0);
                counts.put("scripts", 0);
                counts.put("namedQueries", 0);
                counts.put("total", 0);
                return counts;
            }

            // Count views (com.inductiveautomation.perspective/views/**/view.json)
            Path viewsDir = projectDir.resolve("com.inductiveautomation.perspective").resolve("views");
            int viewCount = countResourceFiles(viewsDir, "view.json");
            counts.put("views", viewCount);

            // Count scripts (ignition/script-python/**/code.py)
            Path scriptsDir = projectDir.resolve("ignition").resolve("script-python");
            int scriptCount = countResourceFiles(scriptsDir, "code.py");
            counts.put("scripts", scriptCount);

            // Count named queries (ignition/named-query/**/query.json or query.sql)
            Path queriesDir = projectDir.resolve("ignition").resolve("named-query");
            int queryCount = countResourceFiles(queriesDir, "query.json");
            if (queryCount == 0) {
                // Fallback to counting query.sql if query.json not found
                queryCount = countResourceFiles(queriesDir, "query.sql");
            }
            counts.put("namedQueries", queryCount);

            counts.put("total", viewCount + scriptCount + queryCount);

            logger.debug("Resource counts for {}: views={}, scripts={}, queries={}",
                    projectName, viewCount, scriptCount, queryCount);

        } catch (Exception e) {
            logger.debug("Failed to count resources for {}: {}", projectName, e.getMessage());
            counts.put("views", 0);
            counts.put("scripts", 0);
            counts.put("namedQueries", 0);
            counts.put("total", 0);
        }

        return counts;
    }

    /**
     * Counts files with a specific name within a directory tree.
     */
    private int countResourceFiles(Path directory, String filename) {
        if (!Files.exists(directory)) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            return (int) paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .count();
        } catch (Exception e) {
            logger.debug("Error counting {} files in {}: {}", filename, directory, e.getMessage());
            return 0;
        }
    }
}
