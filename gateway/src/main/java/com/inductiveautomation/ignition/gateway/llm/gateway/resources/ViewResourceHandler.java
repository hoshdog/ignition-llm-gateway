package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.ViewConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resource handler for Perspective views.
 * Provides CRUD operations on view configurations.
 *
 * <p>This handler uses direct filesystem access to manage Perspective views
 * stored as JSON resources within Ignition projects (Ignition 8.3+).</p>
 *
 * <p><b>Note:</b> Changes made via filesystem require a project resource scan
 * or Gateway restart to be reflected in the Designer.</p>
 */
public class ViewResourceHandler implements ResourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(ViewResourceHandler.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final ViewConfigValidator validator;

    public ViewResourceHandler(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;
        this.validator = new ViewConfigValidator();
    }

    @Override
    public String getResourceType() {
        return "perspective-view";
    }

    @Override
    public ActionResult create(CreateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Creating view: {} (correlationId={})", resourcePath, correlationId);

        // Parse path: "ProjectName/Path/To/View"
        ViewPath viewPath;
        try {
            viewPath = ViewPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        // Validate view configuration
        Map<String, Object> payload = action.getPayload();
        ValidationResult validation = validator.validate(payload);
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Check project exists
        if (!projectExists(viewPath.getProjectName())) {
            return ActionResult.failure(correlationId,
                    "Project not found: " + viewPath.getProjectName(),
                    Collections.singletonList("PROJECT_NOT_FOUND"));
        }

        // Check view doesn't already exist
        if (viewExists(viewPath)) {
            return ActionResult.failure(correlationId,
                    "View already exists: " + resourcePath,
                    Collections.singletonList("CONFLICT"));
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            return ActionResult.dryRun(correlationId,
                    "Would create view: " + resourcePath,
                    buildViewPreview(viewPath, payload));
        }

        try {
            Map<String, Object> result = createViewInternal(viewPath, payload);

            auditLogger.logResourceCreated("view", resourcePath, auth);
            logger.info("Created view: {} by user {}", resourcePath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "View created successfully: " + resourcePath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to create view: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to create view: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult read(ReadResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Reading view: {} (correlationId={})", resourcePath, correlationId);

        // Support listing views with wildcard
        if (resourcePath.endsWith("/*")) {
            return listViews(correlationId, resourcePath.substring(0, resourcePath.length() - 2));
        }

        ViewPath viewPath;
        try {
            viewPath = ViewPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        try {
            if (!viewExists(viewPath)) {
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("View not found: " + resourcePath)
                        .errors(Collections.singletonList("NOT_FOUND"))
                        .build();
            }

            Map<String, Object> viewConfig = readViewInternal(viewPath);

            return ActionResult.success(correlationId,
                    "View read successfully",
                    viewConfig);

        } catch (Exception e) {
            logger.error("Failed to read view: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to read view: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult update(UpdateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Updating view: {} (correlationId={})", resourcePath, correlationId);

        ViewPath viewPath;
        try {
            viewPath = ViewPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        if (!viewExists(viewPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("View not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        Map<String, Object> payload = action.getPayload();
        if (payload == null || payload.isEmpty()) {
            return ActionResult.failure(correlationId,
                    "Update payload is required",
                    Collections.singletonList("payload is empty"));
        }

        try {
            // Read existing view for merge/preview
            Map<String, Object> existingView = readViewInternal(viewPath);

            // Apply changes
            Map<String, Object> updatedView;
            if (payload.containsKey("jsonPatch")) {
                // Apply JSON Patch
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> patchOps = (List<Map<String, Object>>) payload.get("jsonPatch");
                updatedView = applyJsonPatch(existingView, patchOps);
            } else if (payload.containsKey("changes")) {
                // Merge changes
                @SuppressWarnings("unchecked")
                Map<String, Object> changes = (Map<String, Object>) payload.get("changes");
                updatedView = mergeChanges(existingView, changes);
            } else {
                // Direct payload update
                updatedView = mergeChanges(existingView, payload);
            }

            // Validate result
            ValidationResult validation = validator.validate(updatedView);
            if (!validation.isValid()) {
                return buildValidationFailureResult(correlationId, validation);
            }

            // Handle dry run
            if (action.getOptions().isDryRun()) {
                Map<String, Object> diff = computeDiff(existingView, updatedView);
                return ActionResult.dryRun(correlationId,
                        "Would update view: " + resourcePath,
                        Map.of(
                                "viewPath", resourcePath,
                                "changes", diff
                        ));
            }

            // Save
            Map<String, Object> result = saveViewInternal(viewPath, updatedView);

            String comment = payload.containsKey("_comment") ?
                    (String) payload.get("_comment") : null;
            auditLogger.logResourceUpdated("view", resourcePath, auth, comment);
            logger.info("Updated view: {} by user {}", resourcePath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "View updated successfully: " + resourcePath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to update view: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to update view: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult delete(DeleteResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Deleting view: {} (correlationId={})", resourcePath, correlationId);

        ViewPath viewPath;
        try {
            viewPath = ViewPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        if (!viewExists(viewPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("View not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        // Require force=true for delete
        if (!action.getOptions().isForce()) {
            Map<String, Object> confirmData = new LinkedHashMap<>();
            confirmData.put("viewPath", resourcePath);

            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.PENDING_CONFIRMATION)
                    .message("Delete view '" + resourcePath + "'? This cannot be undone. Set force=true to confirm.")
                    .data(confirmData)
                    .build();
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            return ActionResult.dryRun(correlationId,
                    "Would delete view: " + resourcePath,
                    Map.of("viewPath", resourcePath));
        }

        try {
            deleteViewInternal(viewPath);

            auditLogger.logResourceDeleted("view", resourcePath, auth);
            logger.info("Deleted view: {} by user {}", resourcePath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "View deleted successfully: " + resourcePath,
                    Map.of("viewPath", resourcePath, "deleted", true));

        } catch (Exception e) {
            logger.error("Failed to delete view: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to delete view: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    // ===== Internal helper methods using filesystem access =====

    /**
     * Checks if a project exists by checking for its directory.
     */
    private boolean projectExists(String projectName) {
        logger.debug("Checking if project exists: {}", projectName);
        if (projectName == null || projectName.isEmpty()) {
            return false;
        }
        try {
            Path dataDir = getIgnitionDataDir();
            Path projectDir = dataDir.resolve("projects").resolve(projectName);
            return Files.exists(projectDir) && Files.isDirectory(projectDir);
        } catch (Exception e) {
            logger.debug("Error checking project existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a view exists by checking for view.json.
     */
    private boolean viewExists(ViewPath viewPath) {
        logger.debug("Checking if view exists: {}/{}", viewPath.getProjectName(), viewPath.getViewPath());
        try {
            Path viewFile = getViewFilePath(viewPath);
            return Files.exists(viewFile);
        } catch (Exception e) {
            logger.debug("Error checking view existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the path to view.json for a given view.
     */
    private Path getViewFilePath(ViewPath viewPath) {
        Path dataDir = getIgnitionDataDir();
        return dataDir.resolve("projects")
                .resolve(viewPath.getProjectName())
                .resolve("com.inductiveautomation.perspective")
                .resolve("views")
                .resolve(viewPath.getViewPath())
                .resolve("view.json");
    }

    /**
     * Gets the path to the view directory.
     */
    private Path getViewDirPath(ViewPath viewPath) {
        Path dataDir = getIgnitionDataDir();
        return dataDir.resolve("projects")
                .resolve(viewPath.getProjectName())
                .resolve("com.inductiveautomation.perspective")
                .resolve("views")
                .resolve(viewPath.getViewPath());
    }

    /**
     * Creates a view using direct filesystem access (Ignition 8.3+).
     */
    private Map<String, Object> createViewInternal(ViewPath viewPath, Map<String, Object> config) throws Exception {
        logger.info("Creating view: {}/{}", viewPath.getProjectName(), viewPath.getViewPath());

        Path viewDir = getViewDirPath(viewPath);

        // Create directory structure
        Files.createDirectories(viewDir);

        // Extract view name from path
        String viewName = viewPath.getViewPath();
        if (viewName.contains("/")) {
            viewName = viewName.substring(viewName.lastIndexOf('/') + 1);
        }

        // Build COMPLETE view.json structure
        Map<String, Object> completeView = createCompleteViewStructure(viewName, config);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String viewJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(completeView);

        // Write view.json
        Path viewFile = viewDir.resolve("view.json");
        Files.writeString(viewFile, viewJson, StandardCharsets.UTF_8);

        // Write resource.json (metadata) with proper structure
        Map<String, Object> resourceMeta = createResourceJson();

        Path resourceFile = viewDir.resolve("resource.json");
        Files.writeString(resourceFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resourceMeta), StandardCharsets.UTF_8);

        logger.info("Created view at: {}", viewDir);

        // Trigger project resource scan for immediate visibility
        boolean scanTriggered = triggerProjectResourceScan(viewPath.getProjectName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewPath", viewPath.toString());
        result.put("status", "created");
        result.put("filesystemPath", viewDir.toString());
        result.put("timestamp", Instant.now().toString());
        result.put("resourceScanTriggered", scanTriggered);
        if (!scanTriggered) {
            result.put("note", "View created via filesystem. Use File > Update Project in Designer, or restart Gateway to see the view.");
        } else {
            result.put("note", "View created. Use File > Update Project in Designer to see it immediately.");
        }

        return result;
    }

    /**
     * Triggers a project resource scan using the official ProjectManager API.
     * This causes the Gateway to detect file-based resource changes.
     *
     * @param projectName The project to scan (currently unused, but kept for API compatibility)
     * @return true if scan was triggered successfully, false otherwise
     */
    private boolean triggerProjectResourceScan(String projectName) {
        try {
            ProjectManager projectManager = gatewayContext.getProjectManager();
            if (projectManager != null) {
                projectManager.requestScan();
                logger.info("Project resource scan triggered successfully via ProjectManager for: {}", projectName);
                return true;
            } else {
                logger.warn("ProjectManager not available - resource scan not triggered");
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to trigger project resource scan: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Creates a complete Perspective view structure with all required fields.
     * This ensures views are properly recognized by Ignition Designer.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> createCompleteViewStructure(String viewName, Map<String, Object> payload) {
        Map<String, Object> viewJson = new LinkedHashMap<>();

        // 1. Meta section (REQUIRED for Designer recognition)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", viewName);
        if (payload.containsKey("title")) {
            meta.put("title", payload.get("title"));
        }
        if (payload.containsKey("description")) {
            meta.put("description", payload.get("description"));
        }
        viewJson.put("meta", meta);

        // 2. Standard sections (REQUIRED - even if empty)
        viewJson.put("params", payload.getOrDefault("params", new LinkedHashMap<>()));
        viewJson.put("custom", payload.getOrDefault("custom", new LinkedHashMap<>()));
        viewJson.put("propConfig", payload.getOrDefault("propConfig", new LinkedHashMap<>()));
        viewJson.put("data", payload.getOrDefault("data", new LinkedHashMap<>()));

        // Props section with required defaultSize
        @SuppressWarnings("unchecked")
        Map<String, Object> props = payload.containsKey("props")
            ? new LinkedHashMap<>((Map<String, Object>) payload.get("props"))
            : new LinkedHashMap<>();

        // Ensure defaultSize exists (required for Designer)
        if (!props.containsKey("defaultSize")) {
            Map<String, Object> defaultSize = new LinkedHashMap<>();
            defaultSize.put("width", 800);
            defaultSize.put("height", 600);
            props.put("defaultSize", defaultSize);
        }
        viewJson.put("props", props);

        // 3. Root component with complete structure
        Map<String, Object> root;
        if (payload.containsKey("root")) {
            root = ensureCompleteRootStructure((Map<String, Object>) payload.get("root"));
        } else {
            root = createDefaultRoot();
        }
        viewJson.put("root", root);

        return viewJson;
    }

    /**
     * Creates a default root component with all required fields for Perspective.
     */
    private Map<String, Object> createDefaultRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "ia.container.flex");
        root.put("version", 0);
        root.put("id", "root");

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "root");
        root.put("meta", meta);

        // Position
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("grow", 1);
        position.put("shrink", 0);
        position.put("basis", "auto");
        root.put("position", position);

        // PropConfig
        root.put("propConfig", new LinkedHashMap<>());

        // Props with default flex direction
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("direction", "column");
        props.put("style", new LinkedHashMap<>());
        root.put("props", props);

        // Empty children array
        root.put("children", new ArrayList<>());

        return root;
    }

    /**
     * Ensures the root component has all required fields for Perspective.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureCompleteRootStructure(Map<String, Object> root) {
        // Create a new map to avoid modifying the original
        Map<String, Object> completeRoot = new LinkedHashMap<>(root);

        // Ensure version
        if (!completeRoot.containsKey("version")) {
            completeRoot.put("version", 0);
        }

        // Ensure root has id
        if (!completeRoot.containsKey("id")) {
            completeRoot.put("id", "root");
        }

        // Ensure meta
        if (!completeRoot.containsKey("meta")) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", "root");
            completeRoot.put("meta", meta);
        }

        // Ensure position
        if (!completeRoot.containsKey("position")) {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("grow", 1);
            position.put("shrink", 0);
            position.put("basis", "auto");
            completeRoot.put("position", position);
        }

        // Ensure propConfig
        if (!completeRoot.containsKey("propConfig")) {
            completeRoot.put("propConfig", new LinkedHashMap<>());
        }

        // Ensure props
        if (!completeRoot.containsKey("props")) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("direction", "column");
            props.put("style", new LinkedHashMap<>());
            completeRoot.put("props", props);
        } else {
            // Ensure props has style if it's a map
            Object propsObj = completeRoot.get("props");
            if (propsObj instanceof Map) {
                Map<String, Object> props = (Map<String, Object>) propsObj;
                if (!props.containsKey("style")) {
                    props.put("style", new LinkedHashMap<>());
                }
            }
        }

        // Ensure children array
        if (!completeRoot.containsKey("children")) {
            completeRoot.put("children", new ArrayList<>());
        }

        // Recursively ensure children have complete structure
        Object childrenObj = completeRoot.get("children");
        if (childrenObj instanceof List) {
            List<Object> children = (List<Object>) childrenObj;
            List<Object> completeChildren = new ArrayList<>();
            for (Object child : children) {
                if (child instanceof Map) {
                    completeChildren.add(ensureCompleteComponentStructure((Map<String, Object>) child));
                } else {
                    completeChildren.add(child);
                }
            }
            completeRoot.put("children", completeChildren);
        }

        return completeRoot;
    }

    /**
     * Ensures a child component has all required fields.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureCompleteComponentStructure(Map<String, Object> component) {
        Map<String, Object> complete = new LinkedHashMap<>(component);

        // Ensure version
        if (!complete.containsKey("version")) {
            complete.put("version", 0);
        }

        // Ensure meta with name
        if (!complete.containsKey("meta")) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", complete.getOrDefault("id", "component"));
            complete.put("meta", meta);
        }

        // Ensure position
        if (!complete.containsKey("position")) {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("grow", 0);
            position.put("shrink", 0);
            position.put("basis", "auto");
            complete.put("position", position);
        }

        // Ensure propConfig
        if (!complete.containsKey("propConfig")) {
            complete.put("propConfig", new LinkedHashMap<>());
        }

        // Ensure props
        if (!complete.containsKey("props")) {
            complete.put("props", new LinkedHashMap<>());
        }

        // Recursively handle children
        Object childrenObj = complete.get("children");
        if (childrenObj instanceof List) {
            List<Object> children = (List<Object>) childrenObj;
            List<Object> completeChildren = new ArrayList<>();
            for (Object child : children) {
                if (child instanceof Map) {
                    completeChildren.add(ensureCompleteComponentStructure((Map<String, Object>) child));
                } else {
                    completeChildren.add(child);
                }
            }
            complete.put("children", completeChildren);
        }

        return complete;
    }

    /**
     * Creates a complete resource.json structure for Perspective views.
     * Scope "A" (All) is required for Designer to see the view.
     */
    private Map<String, Object> createResourceJson() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("scope", "A");  // "A" = All (Gateway + Designer + Client) - REQUIRED for Designer visibility
        resource.put("version", 1);
        resource.put("restricted", false);
        resource.put("overridable", true);
        resource.put("files", Collections.singletonList("view.json"));

        // Attributes with lastModification - timestamp must end with "Z" for ISO format
        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> lastModification = new LinkedHashMap<>();
        lastModification.put("actor", "llm-gateway");
        lastModification.put("timestamp", Instant.now().toString().replace("+00:00", "Z"));
        attributes.put("lastModification", lastModification);
        attributes.put("lastModificationSignature", "");
        resource.put("attributes", attributes);

        return resource;
    }

    /**
     * Reads view configuration via filesystem.
     */
    private Map<String, Object> readViewInternal(ViewPath viewPath) throws Exception {
        logger.debug("Reading view: {}/{}", viewPath.getProjectName(), viewPath.getViewPath());

        Path viewFile = getViewFilePath(viewPath);

        if (!Files.exists(viewFile)) {
            throw new RuntimeException("View not found: " + viewPath);
        }

        String jsonContent = Files.readString(viewFile, StandardCharsets.UTF_8);

        Map<String, Object> viewInfo = new LinkedHashMap<>();
        viewInfo.put("path", viewPath.toString());
        viewInfo.put("project", viewPath.getProjectName());
        viewInfo.put("content", jsonContent);

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> viewContent = mapper.readValue(jsonContent, Map.class);
            viewInfo.put("parsedContent", viewContent);
        } catch (Exception e) {
            logger.debug("Could not parse view JSON: {}", e.getMessage());
        }

        return viewInfo;
    }

    /**
     * Saves an updated view.
     */
    private Map<String, Object> saveViewInternal(ViewPath viewPath, Map<String, Object> config) throws Exception {
        logger.info("Saving view: {}/{}", viewPath.getProjectName(), viewPath.getViewPath());

        Path viewFile = getViewFilePath(viewPath);

        if (!Files.exists(viewFile)) {
            throw new RuntimeException("View not found: " + viewPath);
        }

        // Write updated content
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String viewJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(viewFile, viewJson, StandardCharsets.UTF_8);

        logger.info("Updated view at: {}", viewFile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewPath", viewPath.toString());
        result.put("status", "updated");
        result.put("timestamp", Instant.now().toString());

        return result;
    }

    /**
     * Deletes a view.
     */
    private void deleteViewInternal(ViewPath viewPath) throws Exception {
        logger.info("Deleting view: {}/{}", viewPath.getProjectName(), viewPath.getViewPath());

        Path viewDir = getViewDirPath(viewPath);

        if (!Files.exists(viewDir)) {
            throw new RuntimeException("View not found: " + viewPath);
        }

        // Delete all files in the view directory
        try (Stream<Path> files = Files.list(viewDir)) {
            files.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    logger.warn("Failed to delete file: {}", file, e);
                }
            });
        }

        // Delete the directory
        Files.deleteIfExists(viewDir);

        logger.info("Deleted view directory: {}", viewDir);
    }

    /**
     * Lists views in a project or folder.
     */
    private ActionResult listViews(String correlationId, String basePath) {
        // Parse the base path
        String projectName;
        String folderPath = "";

        int firstSlash = basePath.indexOf('/');
        if (firstSlash == -1) {
            projectName = basePath;
        } else {
            projectName = basePath.substring(0, firstSlash);
            folderPath = basePath.substring(firstSlash + 1);
        }

        return listViewsViaFilesystem(correlationId, projectName, folderPath);
    }

    /**
     * Lists views via filesystem.
     */
    private ActionResult listViewsViaFilesystem(String correlationId, String projectName, String folderPath) {
        try {
            Path dataDir = getIgnitionDataDir();
            Path viewsDir = dataDir.resolve("projects")
                    .resolve(projectName)
                    .resolve("com.inductiveautomation.perspective")
                    .resolve("views");

            if (!Files.exists(viewsDir)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("project", projectName);
                result.put("views", new ArrayList<>());
                result.put("viewCount", 0);
                result.put("message", "No views directory found for project: " + projectName);
                return ActionResult.success(correlationId, "No views found", result);
            }

            // If folder path specified, adjust the search directory
            Path searchDir = folderPath.isEmpty() ? viewsDir : viewsDir.resolve(folderPath);
            if (!Files.exists(searchDir)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("project", projectName);
                result.put("folderPath", folderPath);
                result.put("views", new ArrayList<>());
                result.put("viewCount", 0);
                return ActionResult.success(correlationId, "No views found in folder", result);
            }

            List<Map<String, Object>> viewList = new ArrayList<>();

            // Walk directory to find view.json files
            try (Stream<Path> paths = Files.walk(viewsDir)) {
                paths.filter(p -> p.getFileName().toString().equals("view.json"))
                        .forEach(viewFile -> {
                            Path relativePath = viewsDir.relativize(viewFile.getParent());
                            String viewPathStr = relativePath.toString().replace("\\", "/");

                            // Filter by folder path if specified
                            if (!folderPath.isEmpty() && !viewPathStr.startsWith(folderPath)) {
                                return;
                            }

                            Map<String, Object> viewInfo = new LinkedHashMap<>();
                            viewInfo.put("path", viewPathStr);
                            viewInfo.put("project", projectName);
                            viewList.add(viewInfo);
                        });
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("project", projectName);
            result.put("folderPath", folderPath);
            result.put("views", viewList);
            result.put("viewCount", viewList.size());
            result.put("source", "filesystem");

            return ActionResult.success(correlationId,
                    "Found " + viewList.size() + " views",
                    result);

        } catch (Exception e) {
            logger.error("Failed to list views via filesystem", e);
            return ActionResult.failure(correlationId,
                    "Failed to list views: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Gets the Ignition data directory.
     */
    private Path getIgnitionDataDir() {
        // Try to get from GatewayContext
        try {
            return gatewayContext.getSystemManager().getDataDir().toPath();
        } catch (Exception e) {
            logger.debug("Could not get data dir from SystemManager: {}", e.getMessage());
        }

        // Fallback to common locations
        String ignitionHome = System.getenv("IGNITION_HOME");
        if (ignitionHome != null) {
            return Path.of(ignitionHome, "data");
        }

        // Windows default
        Path windowsDefault = Path.of("C:", "Program Files", "Inductive Automation", "Ignition", "data");
        if (Files.exists(windowsDefault)) {
            return windowsDefault;
        }

        // Linux default
        Path linuxDefault = Path.of("/usr", "local", "ignition", "data");
        if (Files.exists(linuxDefault)) {
            return linuxDefault;
        }

        throw new RuntimeException("Could not determine Ignition data directory");
    }

    /**
     * Applies JSON Patch operations to a view.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyJsonPatch(Map<String, Object> view,
                                                List<Map<String, Object>> patchOps) {
        // Simplified JSON Patch implementation
        Map<String, Object> result = new LinkedHashMap<>(view);

        for (Map<String, Object> op : patchOps) {
            String operation = (String) op.get("op");
            String path = (String) op.get("path");
            Object value = op.get("value");

            // Parse path segments
            String[] segments = path.split("/");

            if ("replace".equals(operation)) {
                setNestedValue(result, segments, value);
            } else if ("add".equals(operation)) {
                setNestedValue(result, segments, value);
            } else if ("remove".equals(operation)) {
                removeNestedValue(result, segments);
            }
        }

        return result;
    }

    /**
     * Merges changes into an existing view.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeChanges(Map<String, Object> existing,
                                              Map<String, Object> changes) {
        Map<String, Object> result = new LinkedHashMap<>(existing);

        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map && result.get(key) instanceof Map) {
                // Recursive merge for nested maps
                result.put(key, mergeChanges(
                        (Map<String, Object>) result.get(key),
                        (Map<String, Object>) value));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Computes the difference between two view configurations.
     */
    private Map<String, Object> computeDiff(Map<String, Object> before,
                                             Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = before.get(key);

            if (!java.util.Objects.equals(oldValue, newValue)) {
                diff.put(key, Map.of(
                        "before", oldValue != null ? oldValue : "null",
                        "after", newValue != null ? newValue : "null"
                ));
            }
        }

        return diff;
    }

    private void setNestedValue(Map<String, Object> map, String[] path, Object value) {
        // Simplified implementation - only handles root-level for now
        if (path.length >= 2) {
            map.put(path[1], value);
        }
    }

    private void removeNestedValue(Map<String, Object> map, String[] path) {
        if (path.length >= 2) {
            map.remove(path[1]);
        }
    }

    private Map<String, Object> buildViewPreview(ViewPath viewPath, Map<String, Object> config) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("projectName", viewPath.getProjectName());
        preview.put("viewPath", viewPath.getViewPath());
        preview.put("configuration", config);
        preview.put("action", "CREATE");

        return preview;
    }

    private ActionResult buildValidationFailureResult(String correlationId, ValidationResult validation) {
        List<String> errors = new ArrayList<>();
        for (ValidationResult.ValidationError error : validation.getErrors()) {
            errors.add(error.toString());
        }

        return ActionResult.builder(correlationId)
                .status(ActionResult.Status.FAILURE)
                .message("Validation failed")
                .errors(errors)
                .warnings(validation.getWarnings())
                .build();
    }

    /**
     * Helper class for parsing view paths.
     */
    static class ViewPath {
        private final String projectName;
        private final String viewPath;

        private ViewPath(String projectName, String viewPath) {
            this.projectName = projectName;
            this.viewPath = viewPath;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getViewPath() {
            return viewPath;
        }

        public static ViewPath parse(String fullPath) {
            int firstSlash = fullPath.indexOf('/');
            if (firstSlash == -1) {
                throw new IllegalArgumentException(
                        "Invalid view path. Expected format: 'ProjectName/Path/To/View'");
            }
            return new ViewPath(
                    fullPath.substring(0, firstSlash),
                    fullPath.substring(firstSlash + 1)
            );
        }

        @Override
        public String toString() {
            return projectName + "/" + viewPath;
        }
    }
}
