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
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.ScriptValidator;
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.SecurityScanResult;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Resource handler for Python scripts in Ignition projects.
 * Provides CRUD operations with comprehensive security scanning.
 *
 * <p>This handler uses direct filesystem access to manage Python scripts
 * stored in Ignition projects (Ignition 8.3+).</p>
 *
 * <p>SECURITY: Scripts are the most sensitive resource type because they execute code.
 * This handler implements strict safeguards including:
 * - Blocked patterns for dangerous operations (os.system, eval, etc.)
 * - Protected script locations that cannot be modified
 * - Security scanning with warnings for risky patterns</p>
 */
public class ScriptResourceHandler implements ResourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScriptResourceHandler.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final ScriptValidator validator;

    // Protected script locations that should NEVER be modified via LLM
    private static final Set<String> PROTECTED_FOLDERS = new HashSet<>(Arrays.asList(
            "system/security",
            "system/auth",
            "gateway/startup",
            "gateway/shutdown",
            "gateway/system",
            "security",
            "authentication"
    ));

    public ScriptResourceHandler(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;
        this.validator = new ScriptValidator();
    }

    @Override
    public String getResourceType() {
        return "script";
    }

    @Override
    public ActionResult create(CreateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Creating script: {} (correlationId={})", resourcePath, correlationId);

        // Parse path
        ScriptPath scriptPath;
        try {
            scriptPath = ScriptPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        // Check protected locations
        if (isProtectedScript(scriptPath)) {
            logSecurityEvent("BLOCKED_PROTECTED_SCRIPT_CREATE", scriptPath.toString(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Cannot create scripts in protected location: " + scriptPath.getFolder())
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        // Get script code
        Map<String, Object> payload = action.getPayload();
        String code = (String) payload.get("code");
        if (code == null || code.trim().isEmpty()) {
            return ActionResult.failure(correlationId,
                    "Script code is required",
                    Collections.singletonList("Script code cannot be empty"));
        }

        // Validate script syntax and structure
        ValidationResult validation = validator.validate(code, scriptPath.getType());
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Security scan
        SecurityScanResult securityScan = validator.securityScan(code);

        // Block dangerous patterns
        if (securityScan.hasBlockedPatterns()) {
            logSecurityEvent("BLOCKED_DANGEROUS_SCRIPT",
                    securityScan.getBlockedPatternsSummary(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Script contains blocked patterns: " + securityScan.getBlockedPatternsSummary())
                    .errors(Collections.singletonList("SECURITY_VIOLATION"))
                    .build();
        }

        // Handle warnings - require acknowledgment
        if (securityScan.hasWarnings()) {
            Boolean acknowledge = (Boolean) payload.get("acknowledgeWarnings");
            if (acknowledge == null || !acknowledge) {
                Map<String, Object> warningData = new LinkedHashMap<>();
                warningData.put("scriptPath", scriptPath.toString());
                warningData.put("warnings", securityScan.getWarningSummaries());
                warningData.put("message", "Script contains patterns that require review. " +
                        "Set acknowledgeWarnings=true to proceed.");

                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.PENDING_CONFIRMATION)
                        .message("Script contains " + securityScan.getWarnings().size() +
                                " patterns that require review")
                        .data(warningData)
                        .warnings(securityScan.getWarningSummaries())
                        .build();
            }
        }

        // Check project exists
        if (!projectExists(scriptPath.getProjectName())) {
            return ActionResult.failure(correlationId,
                    "Project not found: " + scriptPath.getProjectName(),
                    Collections.singletonList("PROJECT_NOT_FOUND"));
        }

        // Check script doesn't already exist
        if (scriptExists(scriptPath)) {
            return ActionResult.failure(correlationId,
                    "Script already exists: " + scriptPath,
                    Collections.singletonList("CONFLICT"));
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("path", scriptPath.toString());
            preview.put("lineCount", countLines(code));
            preview.put("scriptType", scriptPath.getType().name());
            if (!securityScan.getWarningSummaries().isEmpty()) {
                preview.put("securityWarnings", securityScan.getWarningSummaries());
            }
            if (!validation.getInfos().isEmpty()) {
                preview.put("info", validation.getInfos());
            }

            return ActionResult.dryRun(correlationId,
                    "Would create script: " + scriptPath,
                    preview);
        }

        try {
            Map<String, Object> result = createScriptInternal(scriptPath, code, payload);

            auditLogger.logResourceCreated("script", scriptPath.toString(), auth);
            logger.info("Created script: {} by user {}", scriptPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Script created successfully: " + scriptPath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to create script: {}", scriptPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to create script: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult read(ReadResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Reading script: {} (correlationId={})", resourcePath, correlationId);

        // Support listing scripts with wildcard
        if (resourcePath.endsWith("/*")) {
            return listScripts(correlationId, resourcePath.substring(0, resourcePath.length() - 2));
        }

        ScriptPath scriptPath;
        try {
            scriptPath = ScriptPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        try {
            if (!scriptExists(scriptPath)) {
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Script not found: " + resourcePath)
                        .errors(Collections.singletonList("NOT_FOUND"))
                        .build();
            }

            Map<String, Object> scriptData = readScriptInternal(scriptPath);

            return ActionResult.success(correlationId,
                    "Script read successfully",
                    scriptData);

        } catch (Exception e) {
            logger.error("Failed to read script: {}", resourcePath, e);
            return ActionResult.failure(correlationId,
                    "Failed to read script: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult update(UpdateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Updating script: {} (correlationId={})", resourcePath, correlationId);

        ScriptPath scriptPath;
        try {
            scriptPath = ScriptPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        // Check protected locations
        if (isProtectedScript(scriptPath)) {
            logSecurityEvent("BLOCKED_PROTECTED_SCRIPT_UPDATE", scriptPath.toString(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Cannot modify scripts in protected location: " + scriptPath.getFolder())
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        if (!scriptExists(scriptPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Script not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        Map<String, Object> payload = action.getPayload();
        String newCode = (String) payload.get("code");
        if (newCode == null || newCode.trim().isEmpty()) {
            return ActionResult.failure(correlationId,
                    "New script code is required",
                    Collections.singletonList("code is required"));
        }

        // Validate new code
        ValidationResult validation = validator.validate(newCode, scriptPath.getType());
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Security scan
        SecurityScanResult securityScan = validator.securityScan(newCode);

        if (securityScan.hasBlockedPatterns()) {
            logSecurityEvent("BLOCKED_DANGEROUS_SCRIPT_UPDATE",
                    securityScan.getBlockedPatternsSummary(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Script contains blocked patterns: " + securityScan.getBlockedPatternsSummary())
                    .errors(Collections.singletonList("SECURITY_VIOLATION"))
                    .build();
        }

        // Handle warnings
        if (securityScan.hasWarnings()) {
            Boolean acknowledge = (Boolean) payload.get("acknowledgeWarnings");
            if (acknowledge == null || !acknowledge) {
                Map<String, Object> warningData = new LinkedHashMap<>();
                warningData.put("scriptPath", scriptPath.toString());
                warningData.put("warnings", securityScan.getWarningSummaries());

                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.PENDING_CONFIRMATION)
                        .message("Script update contains patterns that require review")
                        .data(warningData)
                        .warnings(securityScan.getWarningSummaries())
                        .build();
            }
        }

        // Handle dry run with diff
        if (action.getOptions().isDryRun()) {
            try {
                Map<String, Object> existingScript = readScriptInternal(scriptPath);
                String existingCode = (String) existingScript.get("code");

                List<String> diff = computeSimpleDiff(existingCode, newCode);

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("path", scriptPath.toString());
                preview.put("diff", diff);
                preview.put("oldLineCount", countLines(existingCode));
                preview.put("newLineCount", countLines(newCode));
                if (!securityScan.getWarningSummaries().isEmpty()) {
                    preview.put("securityWarnings", securityScan.getWarningSummaries());
                }

                return ActionResult.dryRun(correlationId,
                        "Would update script: " + scriptPath,
                    preview);
            } catch (Exception e) {
                return ActionResult.failure(correlationId,
                        "Failed to read script for dry-run: " + e.getMessage(),
                        Collections.singletonList(e.getMessage()));
            }
        }

        try {
            Map<String, Object> result = updateScriptInternal(scriptPath, newCode, payload);

            String comment = action.getOptions().getComment();
            auditLogger.logResourceUpdated("script", scriptPath.toString(), auth, comment);
            logger.info("Updated script: {} by user {}", scriptPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Script updated successfully: " + scriptPath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to update script: {}", scriptPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to update script: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult delete(DeleteResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String resourcePath = action.getResourcePath();

        logger.debug("Deleting script: {} (correlationId={})", resourcePath, correlationId);

        ScriptPath scriptPath;
        try {
            scriptPath = ScriptPath.parse(resourcePath);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(correlationId, e.getMessage(),
                    Collections.singletonList("INVALID_PATH"));
        }

        // Check protected locations
        if (isProtectedScript(scriptPath)) {
            logSecurityEvent("BLOCKED_PROTECTED_SCRIPT_DELETE", scriptPath.toString(), auth);
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Cannot delete scripts in protected location: " + scriptPath.getFolder())
                    .errors(Collections.singletonList("FORBIDDEN"))
                    .build();
        }

        if (!scriptExists(scriptPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Script not found: " + resourcePath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        // Require force=true for delete
        if (!action.getOptions().isForce()) {
            Map<String, Object> confirmData = new LinkedHashMap<>();
            confirmData.put("scriptPath", resourcePath);
            confirmData.put("scriptType", scriptPath.getType().name());

            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.PENDING_CONFIRMATION)
                    .message("Delete script '" + resourcePath + "'? This cannot be undone. Set force=true to confirm.")
                    .data(confirmData)
                    .build();
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            return ActionResult.dryRun(correlationId,
                    "Would delete script: " + resourcePath,
                    Map.of("scriptPath", resourcePath));
        }

        try {
            deleteScriptInternal(scriptPath);

            auditLogger.logResourceDeleted("script", scriptPath.toString(), auth);
            logger.info("Deleted script: {} by user {}", scriptPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Script deleted successfully: " + scriptPath,
                    Map.of("scriptPath", resourcePath, "deleted", true));

        } catch (Exception e) {
            logger.error("Failed to delete script: {}", scriptPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to delete script: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    // ===== Security helper methods =====

    /**
     * Checks if a script is in a protected location.
     */
    private boolean isProtectedScript(ScriptPath path) {
        String folder = path.getFolder().toLowerCase();
        String fullPath = (path.getType().getPathPrefix() + "/" + folder).toLowerCase();

        for (String protected_folder : PROTECTED_FOLDERS) {
            if (folder.startsWith(protected_folder) ||
                    folder.equals(protected_folder) ||
                    fullPath.contains(protected_folder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Logs a security event to the audit log.
     */
    private void logSecurityEvent(String eventType, String details, AuthContext auth) {
        logger.warn("[SECURITY] {} - {} - user={}, source={}",
                eventType, details, auth.getUserId(), auth.getUserSource());
        // Also log to audit
        auditLogger.logSystemEvent(eventType,
                details + " by " + auth.getUserId(),
                Map.of("user", auth.getUserId(), "details", details));
    }

    // ===== Internal helper methods using ProjectManager API =====

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
     * Checks if a script exists.
     */
    private boolean scriptExists(ScriptPath scriptPath) {
        logger.debug("Checking if script exists: {}", scriptPath);
        try {
            // Check via filesystem
            Path scriptFile = getScriptFilePath(scriptPath);
            return Files.exists(scriptFile);
        } catch (Exception e) {
            logger.debug("Error checking script existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a script using filesystem access.
     */
    private Map<String, Object> createScriptInternal(ScriptPath scriptPath, String code,
                                                      Map<String, Object> payload) throws Exception {
        logger.info("Creating script: {}", scriptPath);

        Path scriptDir = getScriptDirPath(scriptPath);
        Files.createDirectories(scriptDir);

        // Write code.py
        Path codeFile = scriptDir.resolve("code.py");
        Files.writeString(codeFile, code, StandardCharsets.UTF_8);

        // Write resource.json (metadata) with proper lastModification
        // Scope "G" (Gateway) matches Designer-created resources
        Map<String, Object> resourceMeta = new LinkedHashMap<>();
        resourceMeta.put("scope", "G");  // "G" = Gateway - matches Designer-created resources
        resourceMeta.put("version", 1);
        resourceMeta.put("restricted", false);
        resourceMeta.put("overridable", true);
        resourceMeta.put("files", Collections.singletonList("code.py"));

        // Add proper attributes with lastModification - timestamp must end with "Z" for ISO format
        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> lastModification = new LinkedHashMap<>();
        lastModification.put("actor", "llm-gateway");
        // Truncate to milliseconds - Ignition doesn't support nanosecond precision
        lastModification.put("timestamp", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        attributes.put("lastModification", lastModification);
        attributes.put("lastModificationSignature", "");
        resourceMeta.put("attributes", attributes);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Path resourceFile = scriptDir.resolve("resource.json");
        Files.writeString(resourceFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resourceMeta), StandardCharsets.UTF_8);

        logger.info("Created script at: {}", scriptDir);

        // Trigger project resource scan for immediate visibility
        boolean scanTriggered = triggerProjectResourceScan(scriptPath.getProjectName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scriptPath", scriptPath.toString());
        result.put("status", "created");
        result.put("filesystemPath", scriptDir.toString());
        result.put("lineCount", countLines(code));
        result.put("timestamp", Instant.now().toString());
        result.put("resourceScanTriggered", scanTriggered);
        if (!scanTriggered) {
            result.put("note", "Script created via filesystem. Use File > Update Project in Designer, or restart Gateway to see the script.");
        } else {
            result.put("note", "Script created. Use File > Update Project in Designer to see it immediately.");
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
     * Reads script code via filesystem.
     */
    private Map<String, Object> readScriptInternal(ScriptPath scriptPath) throws Exception {
        logger.debug("Reading script: {}", scriptPath);

        Path codeFile = getScriptFilePath(scriptPath);
        if (!Files.exists(codeFile)) {
            throw new RuntimeException("Script not found: " + scriptPath);
        }

        String code = Files.readString(codeFile, StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", scriptPath.toString());
        result.put("project", scriptPath.getProjectName());
        result.put("code", code);
        result.put("lineCount", countLines(code));
        result.put("scriptType", scriptPath.getType().name());

        return result;
    }

    /**
     * Updates a script.
     */
    private Map<String, Object> updateScriptInternal(ScriptPath scriptPath, String code,
                                                      Map<String, Object> payload) throws Exception {
        logger.info("Updating script: {}", scriptPath);

        Path codeFile = getScriptFilePath(scriptPath);
        if (!Files.exists(codeFile)) {
            throw new RuntimeException("Script not found: " + scriptPath);
        }

        // Write updated code
        Files.writeString(codeFile, code, StandardCharsets.UTF_8);

        logger.info("Updated script at: {}", codeFile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scriptPath", scriptPath.toString());
        result.put("status", "updated");
        result.put("lineCount", countLines(code));
        result.put("timestamp", Instant.now().toString());

        return result;
    }

    /**
     * Deletes a script.
     */
    private void deleteScriptInternal(ScriptPath scriptPath) throws Exception {
        logger.info("Deleting script: {}", scriptPath);

        Path scriptDir = getScriptDirPath(scriptPath);
        if (!Files.exists(scriptDir)) {
            throw new RuntimeException("Script not found: " + scriptPath);
        }

        // Delete all files in the script directory
        try (Stream<Path> files = Files.list(scriptDir)) {
            files.forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    logger.warn("Failed to delete file: {}", file, e);
                }
            });
        }

        // Delete the directory
        Files.deleteIfExists(scriptDir);

        logger.info("Deleted script directory: {}", scriptDir);
    }

    /**
     * Lists scripts in a project or folder.
     */
    private ActionResult listScripts(String correlationId, String basePath) {
        // Parse the base path
        String[] parts = basePath.split("/", 3);
        String projectName = parts.length > 0 ? parts[0] : "";
        String scriptType = parts.length > 1 ? parts[1] : "";
        String folderPath = parts.length > 2 ? parts[2] : "";

        return listScriptsViaFilesystem(correlationId, projectName, scriptType, folderPath);
    }

    /**
     * Lists scripts via filesystem.
     */
    private ActionResult listScriptsViaFilesystem(String correlationId, String projectName,
                                                   String scriptType, String folderPath) {
        try {
            Path dataDir = getIgnitionDataDir();
            Path scriptsDir = dataDir.resolve("projects")
                    .resolve(projectName)
                    .resolve("ignition")
                    .resolve("script-python");

            if (!Files.exists(scriptsDir)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("project", projectName);
                result.put("scripts", new ArrayList<>());
                result.put("scriptCount", 0);
                result.put("message", "No scripts directory found for project: " + projectName);
                return ActionResult.success(correlationId, "No scripts found", result);
            }

            List<Map<String, Object>> scriptList = new ArrayList<>();

            // Walk directory to find code.py files
            try (Stream<Path> paths = Files.walk(scriptsDir)) {
                paths.filter(p -> p.getFileName().toString().equals("code.py"))
                        .forEach(codeFile -> {
                            Path relativePath = scriptsDir.relativize(codeFile.getParent());
                            String scriptPathStr = relativePath.toString().replace("\\", "/");

                            Map<String, Object> scriptInfo = new LinkedHashMap<>();
                            scriptInfo.put("path", scriptPathStr);
                            scriptInfo.put("project", projectName);
                            scriptList.add(scriptInfo);
                        });
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("project", projectName);
            result.put("scriptType", scriptType);
            result.put("folderPath", folderPath);
            result.put("scripts", scriptList);
            result.put("scriptCount", scriptList.size());
            result.put("source", "filesystem");

            return ActionResult.success(correlationId,
                    "Found " + scriptList.size() + " scripts",
                    result);

        } catch (Exception e) {
            logger.error("Failed to list scripts via filesystem", e);
            return ActionResult.failure(correlationId,
                    "Failed to list scripts: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Gets the script directory path.
     */
    private Path getScriptDirPath(ScriptPath scriptPath) throws Exception {
        Path dataDir = getIgnitionDataDir();
        Path scriptsDir = dataDir.resolve("projects")
                .resolve(scriptPath.getProjectName())
                .resolve("ignition")
                .resolve("script-python");

        // Build path based on script type and folder
        String folder = scriptPath.getFolder();
        if (folder != null && !folder.isEmpty()) {
            scriptsDir = scriptsDir.resolve(folder);
        }
        return scriptsDir.resolve(scriptPath.getScriptName());
    }

    /**
     * Gets the script code.py file path.
     */
    private Path getScriptFilePath(ScriptPath scriptPath) throws Exception {
        return getScriptDirPath(scriptPath).resolve("code.py");
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

    private int countLines(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private List<String> computeSimpleDiff(String oldCode, String newCode) {
        // Simple diff: show added/removed line counts
        List<String> diff = new ArrayList<>();

        int oldLines = countLines(oldCode);
        int newLines = countLines(newCode);

        if (oldLines != newLines) {
            int delta = newLines - oldLines;
            if (delta > 0) {
                diff.add("+" + delta + " lines added");
            } else {
                diff.add(Math.abs(delta) + " lines removed");
            }
        } else {
            diff.add("Line count unchanged (" + newLines + " lines)");
        }

        // Check if content changed
        if (!oldCode.equals(newCode)) {
            diff.add("Content modified");
        }

        return diff;
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
