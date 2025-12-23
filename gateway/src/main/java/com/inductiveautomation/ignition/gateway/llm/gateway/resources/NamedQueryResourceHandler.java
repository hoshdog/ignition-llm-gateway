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
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.NamedQueryValidator;
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.SqlSecurityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resource handler for Named Queries in Ignition projects.
 * Provides CRUD operations with SQL security scanning.
 *
 * <p>This handler uses direct filesystem access to manage Named Queries
 * stored in Ignition projects (Ignition 8.3+).</p>
 */
public class NamedQueryResourceHandler implements ResourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(NamedQueryResourceHandler.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final NamedQueryValidator validator;

    public NamedQueryResourceHandler(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;
        this.validator = new NamedQueryValidator();
    }

    @Override
    public String getResourceType() {
        return "named-query";
    }

    @Override
    public ActionResult create(CreateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Creating named query: {} (correlationId={})", resourcePath, correlationId);

        // Parse path
        NamedQueryPath queryPath;
        try {
            queryPath = NamedQueryPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        Map<String, Object> payload = action.getPayload();

        // Validate query configuration
        ValidationResult validation = validator.validate(payload);
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Security check on SQL
        String sql = String.valueOf(payload.get("query"));
        SqlSecurityResult sqlSecurity = validator.scanSql(sql);

        if (sqlSecurity.hasBlockedPatterns()) {
            logSecurityEvent("BLOCKED_DANGEROUS_SQL",
                    sqlSecurity.getBlockedPatternsSummary(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Query contains blocked SQL patterns: " + sqlSecurity.getBlockedPatternsSummary())
                    .errors(Collections.singletonList("SECURITY_VIOLATION"))
                    .build();
        }

        // Check project exists
        if (!projectExists(queryPath.getProjectName())) {
            return ActionResult.failure(correlationId,
                    "Project not found: " + queryPath.getProjectName(),
                    Collections.singletonList("PROJECT_NOT_FOUND"));
        }

        // Check query doesn't already exist
        if (queryExists(queryPath)) {
            return ActionResult.failure(correlationId,
                    "Named query already exists: " + queryPath,
                    Collections.singletonList("CONFLICT"));
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("path", queryPath.toString());
            preview.put("queryType", payload.get("queryType"));
            preview.put("database", payload.get("database"));
            preview.put("parameterCount", countParameters(sql));
            if (!sqlSecurity.getWarnings().isEmpty()) {
                preview.put("warnings", sqlSecurity.getWarnings());
            }

            return ActionResult.dryRun(correlationId,
                    "Would create named query: " + queryPath,
                    preview);
        }

        try {
            Map<String, Object> result = createQueryInternal(queryPath, payload);

            auditLogger.logResourceCreated("named-query", queryPath.toString(), auth);
            logger.info("Created named query: {} by user {}", queryPath, auth.getUserId());

            ActionResult.Builder resultBuilder = ActionResult.builder(correlationId)
                    .status(ActionResult.Status.SUCCESS)
                    .message("Named query created successfully: " + queryPath)
                    .data(result);

            if (!sqlSecurity.getWarnings().isEmpty()) {
                resultBuilder.warnings(sqlSecurity.getWarnings());
            }

            return resultBuilder.build();

        } catch (Exception e) {
            logger.error("Failed to create named query: {}", queryPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to create named query: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult read(ReadResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Reading named query: {} (correlationId={})", resourcePath, correlationId);

        // Support listing queries with wildcard
        if (resourcePath.endsWith("/*")) {
            return listQueries(correlationId, resourcePath.substring(0, resourcePath.length() - 2));
        }

        NamedQueryPath queryPath;
        try {
            queryPath = NamedQueryPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        try {
            if (!queryExists(queryPath)) {
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Named query not found: " + resourcePath)
                        .errors(Collections.singletonList("NOT_FOUND"))
                        .build();
            }

            Map<String, Object> queryConfig = readQueryInternal(queryPath);

            return ActionResult.success(correlationId,
                    "Named query read successfully",
                    queryConfig);

        } catch (Exception e) {
            logger.error("Failed to read named query: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to read named query: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult update(UpdateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Updating named query: {} (correlationId={})", resourcePath, correlationId);

        NamedQueryPath queryPath;
        try {
            queryPath = NamedQueryPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        if (!queryExists(queryPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Named query not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        Map<String, Object> payload = action.getPayload();

        // Validate changes
        ValidationResult validation = validator.validateUpdate(payload);
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Security check if query SQL is being updated
        if (payload.containsKey("query")) {
            String sql = String.valueOf(payload.get("query"));
            SqlSecurityResult sqlSecurity = validator.scanSql(sql);

            if (sqlSecurity.hasBlockedPatterns()) {
                logSecurityEvent("BLOCKED_DANGEROUS_SQL_UPDATE",
                        sqlSecurity.getBlockedPatternsSummary(), auth);
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Query contains blocked SQL patterns: " + sqlSecurity.getBlockedPatternsSummary())
                        .errors(Collections.singletonList("SECURITY_VIOLATION"))
                        .build();
            }
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            try {
                Map<String, Object> existingQuery = readQueryInternal(queryPath);
                Map<String, Object> diff = computeQueryDiff(existingQuery, payload);

                return ActionResult.dryRun(correlationId,
                        "Would update named query: " + queryPath,
                        diff);
            } catch (Exception e) {
                return ActionResult.failure(correlationId,
                        "Failed to read query for dry-run: " + e.getMessage(),
                        Collections.singletonList(e.getMessage()));
            }
        }

        try {
            // Read existing, merge changes, save
            Map<String, Object> existingConfig = readQueryInternal(queryPath);
            Map<String, Object> updatedConfig = mergeQueryConfig(existingConfig, payload);

            Map<String, Object> result = saveQueryInternal(queryPath, updatedConfig);

            String comment = action.getOptions().getComment();
            auditLogger.logResourceUpdated("named-query", queryPath.toString(), auth, comment);
            logger.info("Updated named query: {} by user {}", queryPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Named query updated successfully: " + queryPath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to update named query: {}", queryPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to update named query: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult delete(DeleteResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Deleting named query: {} (correlationId={})", resourcePath, correlationId);

        NamedQueryPath queryPath;
        try {
            queryPath = NamedQueryPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        if (!queryExists(queryPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Named query not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        // Require force=true for delete
        if (!action.getOptions().isForce()) {
            Map<String, Object> confirmData = new LinkedHashMap<>();
            confirmData.put("queryPath", resourcePath);

            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.PENDING_CONFIRMATION)
                    .message("Delete named query '" + resourcePath + "'? This cannot be undone. Set force=true to confirm.")
                    .data(confirmData)
                    .build();
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            return ActionResult.dryRun(correlationId,
                    "Would delete named query: " + resourcePath,
                    Map.of("queryPath", resourcePath));
        }

        try {
            deleteQueryInternal(queryPath);

            auditLogger.logResourceDeleted("named-query", queryPath.toString(), auth);
            logger.info("Deleted named query: {} by user {}", queryPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Named query deleted successfully: " + queryPath,
                    Map.of("queryPath", resourcePath, "deleted", true));

        } catch (Exception e) {
            logger.error("Failed to delete named query: {}", queryPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to delete named query: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    // ===== Internal helper methods using ProjectManager API =====

    private void logSecurityEvent(String eventType, String details, AuthContext auth) {
        logger.warn("[SECURITY] {} - {} - user={}", eventType, details, auth.getUserId());
        auditLogger.logSystemEvent(eventType, details + " by " + auth.getUserId(),
                Map.of("user", auth.getUserId(), "details", details));
    }

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
     * Checks if a named query exists.
     */
    private boolean queryExists(NamedQueryPath queryPath) {
        logger.debug("Checking if named query exists: {}", queryPath);
        try {
            // Check via filesystem
            Path queryFile = getQueryFilePath(queryPath);
            return Files.exists(queryFile);
        } catch (Exception e) {
            logger.debug("Error checking query existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a named query using filesystem access.
     */
    private Map<String, Object> createQueryInternal(NamedQueryPath queryPath,
                                                     Map<String, Object> config) throws Exception {
        logger.info("Creating named query: {}", queryPath);

        Path queryDir = getQueryDirPath(queryPath);
        Files.createDirectories(queryDir);

        // Write query.sql (the SQL query)
        String sql = String.valueOf(config.getOrDefault("query", ""));
        Path sqlFile = queryDir.resolve("query.sql");
        Files.writeString(sqlFile, sql, StandardCharsets.UTF_8);

        // Write resource.json (metadata) with proper lastModification
        // Scope "A" (All) is required for Designer visibility
        Map<String, Object> resourceMeta = new LinkedHashMap<>();
        resourceMeta.put("scope", "A");  // "A" = All (Gateway + Designer + Client) - REQUIRED for Designer visibility
        resourceMeta.put("version", 1);
        resourceMeta.put("restricted", false);
        resourceMeta.put("overridable", true);
        resourceMeta.put("files", Arrays.asList("query.sql", "query.json"));

        // Add proper attributes with lastModification - timestamp must end with "Z" for ISO format
        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> lastModification = new LinkedHashMap<>();
        lastModification.put("actor", "llm-gateway");
        lastModification.put("timestamp", Instant.now().toString().replace("+00:00", "Z"));
        attributes.put("lastModification", lastModification);
        attributes.put("lastModificationSignature", "");
        resourceMeta.put("attributes", attributes);

        // Write query.json (query configuration)
        Map<String, Object> queryConfig = new LinkedHashMap<>();
        queryConfig.put("queryType", config.getOrDefault("queryType", "Select"));
        queryConfig.put("database", config.getOrDefault("database", ""));
        queryConfig.put("parameters", config.getOrDefault("parameters", new ArrayList<>()));
        queryConfig.put("fallbackValue", config.get("fallbackValue"));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        Path resourceFile = queryDir.resolve("resource.json");
        Files.writeString(resourceFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resourceMeta), StandardCharsets.UTF_8);

        Path configFile = queryDir.resolve("query.json");
        Files.writeString(configFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(queryConfig), StandardCharsets.UTF_8);

        logger.info("Created named query at: {}", queryDir);

        // Trigger project resource scan for immediate visibility
        boolean scanTriggered = triggerProjectResourceScan(queryPath.getProjectName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryPath", queryPath.toString());
        result.put("status", "created");
        result.put("filesystemPath", queryDir.toString());
        result.put("timestamp", Instant.now().toString());
        result.put("resourceScanTriggered", scanTriggered);
        if (!scanTriggered) {
            result.put("note", "Query created via filesystem. Use File > Update Project in Designer, or restart Gateway to see the query.");
        } else {
            result.put("note", "Query created. Use File > Update Project in Designer to see it immediately.");
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
     * Reads a named query via filesystem.
     */
    private Map<String, Object> readQueryInternal(NamedQueryPath queryPath) throws Exception {
        logger.debug("Reading named query: {}", queryPath);

        Path queryDir = getQueryDirPath(queryPath);
        Path sqlFile = queryDir.resolve("query.sql");
        Path configFile = queryDir.resolve("query.json");

        if (!Files.exists(sqlFile)) {
            throw new RuntimeException("Named query not found: " + queryPath);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", queryPath.toString());
        result.put("project", queryPath.getProjectName());

        // Read SQL
        result.put("query", Files.readString(sqlFile, StandardCharsets.UTF_8));

        // Read config if exists
        if (Files.exists(configFile)) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> queryConfig = mapper.readValue(Files.readString(configFile), Map.class);
            result.putAll(queryConfig);
        }

        return result;
    }

    /**
     * Saves a named query.
     */
    private Map<String, Object> saveQueryInternal(NamedQueryPath queryPath,
                                                   Map<String, Object> config) throws Exception {
        logger.info("Saving named query: {}", queryPath);

        Path queryDir = getQueryDirPath(queryPath);
        if (!Files.exists(queryDir)) {
            throw new RuntimeException("Named query not found: " + queryPath);
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Update query.sql if provided
        if (config.containsKey("query")) {
            Path sqlFile = queryDir.resolve("query.sql");
            Files.writeString(sqlFile, String.valueOf(config.get("query")), StandardCharsets.UTF_8);
        }

        // Update query.json
        Path configFile = queryDir.resolve("query.json");
        Map<String, Object> queryConfig = new LinkedHashMap<>();
        if (Files.exists(configFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = mapper.readValue(Files.readString(configFile), Map.class);
            queryConfig.putAll(existing);
        }

        // Merge in new values
        for (String key : Arrays.asList("queryType", "database", "parameters", "fallbackValue")) {
            if (config.containsKey(key)) {
                queryConfig.put(key, config.get(key));
            }
        }

        Files.writeString(configFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(queryConfig), StandardCharsets.UTF_8);

        logger.info("Updated named query at: {}", queryDir);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryPath", queryPath.toString());
        result.put("status", "updated");
        result.put("timestamp", Instant.now().toString());

        return result;
    }

    /**
     * Deletes a named query.
     */
    private void deleteQueryInternal(NamedQueryPath queryPath) throws Exception {
        logger.info("Deleting named query: {}", queryPath);

        Path queryDir = getQueryDirPath(queryPath);
        if (!Files.exists(queryDir)) {
            throw new RuntimeException("Named query not found: " + queryPath);
        }

        // Delete all files in the query directory
        try (Stream<Path> files = Files.list(queryDir)) {
            files.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    logger.warn("Failed to delete file: {}", file, e);
                }
            });
        }

        // Delete the directory
        Files.deleteIfExists(queryDir);

        logger.info("Deleted named query directory: {}", queryDir);
    }

    /**
     * Lists named queries in a project or folder.
     */
    private ActionResult listQueries(String correlationId, String basePath) {
        // Parse the base path
        int firstSlash = basePath.indexOf('/');
        String projectName;
        String folderPath = "";

        if (firstSlash == -1) {
            projectName = basePath;
        } else {
            projectName = basePath.substring(0, firstSlash);
            folderPath = basePath.substring(firstSlash + 1);
        }

        return listQueriesViaFilesystem(correlationId, projectName, folderPath);
    }

    /**
     * Lists queries via filesystem.
     */
    private ActionResult listQueriesViaFilesystem(String correlationId, String projectName, String folderPath) {
        try {
            Path dataDir = getIgnitionDataDir();
            Path queriesDir = dataDir.resolve("projects")
                    .resolve(projectName)
                    .resolve("ignition")
                    .resolve("named-query");

            if (!Files.exists(queriesDir)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("project", projectName);
                result.put("queries", new ArrayList<>());
                result.put("queryCount", 0);
                result.put("message", "No queries directory found for project: " + projectName);
                return ActionResult.success(correlationId, "No queries found", result);
            }

            List<Map<String, Object>> queryList = new ArrayList<>();

            // Walk directory to find query.sql files
            try (Stream<Path> paths = Files.walk(queriesDir)) {
                paths.filter(p -> p.getFileName().toString().equals("query.sql"))
                        .forEach(sqlFile -> {
                            Path relativePath = queriesDir.relativize(sqlFile.getParent());
                            String queryPathStr = relativePath.toString().replace("\\", "/");

                            // Filter by folder path if specified
                            if (!folderPath.isEmpty() && !queryPathStr.startsWith(folderPath)) {
                                return;
                            }

                            Map<String, Object> queryInfo = new LinkedHashMap<>();
                            queryInfo.put("path", queryPathStr);
                            queryInfo.put("project", projectName);
                            queryList.add(queryInfo);
                        });
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("project", projectName);
            result.put("folderPath", folderPath);
            result.put("queries", queryList);
            result.put("queryCount", queryList.size());
            result.put("source", "filesystem");

            return ActionResult.success(correlationId,
                    "Found " + queryList.size() + " queries",
                    result);

        } catch (Exception e) {
            logger.error("Failed to list queries via filesystem", e);
            return ActionResult.failure(correlationId,
                    "Failed to list queries: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Gets the query directory path.
     */
    private Path getQueryDirPath(NamedQueryPath queryPath) throws Exception {
        Path dataDir = getIgnitionDataDir();
        Path queriesDir = dataDir.resolve("projects")
                .resolve(queryPath.getProjectName())
                .resolve("ignition")
                .resolve("named-query");

        // Build path based on folder
        String folder = queryPath.getFolder();
        if (folder != null && !folder.isEmpty()) {
            queriesDir = queriesDir.resolve(folder);
        }
        return queriesDir.resolve(queryPath.getQueryName());
    }

    /**
     * Gets the query.sql file path.
     */
    private Path getQueryFilePath(NamedQueryPath queryPath) throws Exception {
        return getQueryDirPath(queryPath).resolve("query.sql");
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeQueryConfig(Map<String, Object> existing,
                                                  Map<String, Object> changes) {
        Map<String, Object> result = new LinkedHashMap<>(existing);

        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip internal fields
            if (key.startsWith("_")) {
                continue;
            }

            if (value instanceof Map && result.get(key) instanceof Map) {
                result.put(key, mergeQueryConfig(
                        (Map<String, Object>) result.get(key),
                        (Map<String, Object>) value));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    private Map<String, Object> computeQueryDiff(Map<String, Object> existing,
                                                  Map<String, Object> changes) {
        Map<String, Object> diff = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) continue;

            Object newValue = entry.getValue();
            Object oldValue = existing.get(key);

            if (!java.util.Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", oldValue);
                change.put("to", newValue);
                diff.put(key, change);
            }
        }

        return diff;
    }

    private int countParameters(String sql) {
        if (sql == null) return 0;

        int count = 0;
        // Count :paramName style
        java.util.regex.Matcher colonMatcher =
                java.util.regex.Pattern.compile(":(\\w+)").matcher(sql);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (colonMatcher.find()) {
            seen.add(colonMatcher.group(1));
        }

        // Count {paramName} style
        java.util.regex.Matcher braceMatcher =
                java.util.regex.Pattern.compile("\\{(\\w+)}").matcher(sql);
        while (braceMatcher.find()) {
            seen.add(braceMatcher.group(1));
        }

        return seen.size();
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
}
