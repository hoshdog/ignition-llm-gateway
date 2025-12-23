package com.inductiveautomation.ignition.gateway.llm.gateway.resources;

import com.inductiveautomation.ignition.common.browsing.Results;
import com.inductiveautomation.ignition.common.model.values.BasicQualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.config.BasicTagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.common.tags.config.TagConfiguration;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.config.types.TagObjectType;
import com.inductiveautomation.ignition.common.tags.model.SecurityContext;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionResult;
import com.inductiveautomation.ignition.gateway.llm.common.model.ValidationResult;
import com.inductiveautomation.ignition.gateway.llm.gateway.audit.AuditLogger;
import com.inductiveautomation.ignition.gateway.llm.gateway.auth.AuthContext;
import com.inductiveautomation.ignition.gateway.llm.gateway.validators.TagConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resource handler for Ignition tags.
 * Provides CRUD operations on tag configuration and values.
 */
public class TagResourceHandler implements ResourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(TagResourceHandler.class);

    private final GatewayContext gatewayContext;
    private final AuditLogger auditLogger;
    private final TagConfigValidator validator;

    public TagResourceHandler(GatewayContext gatewayContext, AuditLogger auditLogger) {
        this.gatewayContext = gatewayContext;
        this.auditLogger = auditLogger;
        this.validator = new TagConfigValidator();
    }

    @Override
    public String getResourceType() {
        return "tag";
    }

    @Override
    public ActionResult create(CreateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String tagPath = action.getResourcePath();

        logger.debug("Creating tag: {} (correlationId={})", tagPath, correlationId);

        // 1. Validate tag configuration
        Map<String, Object> payload = action.getPayload();
        if (payload == null || payload.isEmpty()) {
            return ActionResult.failure(correlationId,
                    "Tag configuration payload is required",
                    Collections.singletonList("payload is empty"));
        }

        ValidationResult validation = validator.validate(payload);
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // 2. Check if tag already exists
        if (tagExists(tagPath)) {
            return ActionResult.failure(correlationId,
                    "Tag already exists: " + tagPath,
                    Collections.singletonList("CONFLICT: Tag path already in use"));
        }

        // 3. Handle dry run
        if (action.getOptions().isDryRun()) {
            return ActionResult.dryRun(correlationId,
                    "Would create tag: " + tagPath,
                    buildTagPreview(tagPath, payload));
        }

        // 4. Create the tag using Ignition API
        try {
            Map<String, Object> result = createTagInternal(tagPath, payload);

            logger.info("Created tag: {} by user {}", tagPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Tag created successfully: " + tagPath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to create tag: {}", tagPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to create tag: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult read(ReadResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String tagPath = action.getResourcePath();

        logger.debug("Reading tag: {} (correlationId={})", tagPath, correlationId);

        // Handle special paths for listing
        if (tagPath == null || tagPath.isEmpty() || tagPath.equals("*")) {
            // List all tag providers
            return listAllProviders(correlationId);
        }

        // Support browsing folder with wildcard
        if (tagPath.endsWith("/*")) {
            return browseTagFolder(correlationId, tagPath.substring(0, tagPath.length() - 2));
        }

        // Handle provider-only paths like "[default]" or "[Sample_Tags]"
        if (tagPath.startsWith("[") && tagPath.endsWith("]")) {
            // Browse the root of this provider
            return browseTagFolder(correlationId, tagPath);
        }

        try {
            // Parse and validate the path
            ParsedTagPath parsed;
            try {
                parsed = parseTagPath(tagPath);
            } catch (IllegalArgumentException e) {
                return ActionResult.failure(correlationId,
                        "Invalid tag path format: " + tagPath,
                        Collections.singletonList("INVALID_PATH"));
            }

            // Check if provider exists
            TagProvider provider = getTagProvider(parsed.providerName);
            if (provider == null) {
                List<String> availableProviders = listProviderNames();
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Tag provider not found: " + parsed.providerName +
                                ". Available: " + availableProviders)
                        .errors(Collections.singletonList("PROVIDER_NOT_FOUND"))
                        .data(Map.of("availableProviders", availableProviders))
                        .build();
            }

            // Check if tag exists
            if (!tagExists(tagPath)) {
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Tag not found: " + tagPath)
                        .errors(Collections.singletonList("NOT_FOUND"))
                        .build();
            }

            // Get tag configuration
            Map<String, Object> tagConfig = readTagInternal(tagPath);

            // Optionally include current value
            if (shouldIncludeValue(action)) {
                Map<String, Object> valueInfo = readTagValue(tagPath);
                tagConfig.put("currentValue", valueInfo);
            }

            return ActionResult.success(correlationId,
                    "Tag read successfully",
                    tagConfig);

        } catch (Exception e) {
            logger.error("Failed to read tag: {}", tagPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to read tag: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult update(UpdateResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String tagPath = action.getResourcePath();

        logger.debug("Updating tag: {} (correlationId={})", tagPath, correlationId);

        // Check tag exists
        if (!tagExists(tagPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Tag not found: " + tagPath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        Map<String, Object> payload = action.getPayload();
        if (payload == null || payload.isEmpty()) {
            return ActionResult.failure(correlationId,
                    "Update payload is required",
                    Collections.singletonList("payload is empty"));
        }

        // Special case: writing tag value only
        // Check if only "value" is present, or if it's marked as value-only write
        boolean isValueOnlyWrite = payload.containsKey("value") &&
                (payload.size() == 1 ||
                 (payload.size() == 2 && payload.containsKey("_writeValueOnly")) ||
                 Boolean.TRUE.equals(payload.get("_writeValueOnly")));

        if (isValueOnlyWrite) {
            return writeTagValue(correlationId, tagPath, payload.get("value"), action.getOptions().isDryRun());
        }

        // Validate configuration changes
        ValidationResult validation = validator.validate(payload);
        if (!validation.isValid()) {
            return buildValidationFailureResult(correlationId, validation);
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("tagPath", tagPath);
            preview.put("changes", payload);
            preview.put("merge", action.isMerge());

            return ActionResult.dryRun(correlationId,
                    "Would update tag: " + tagPath,
                    preview);
        }

        try {
            Map<String, Object> result = updateTagInternal(tagPath, payload, action.isMerge());

            logger.info("Updated tag: {} by user {}", tagPath, auth.getUserId());

            return ActionResult.success(correlationId,
                    "Tag updated successfully: " + tagPath,
                    result);

        } catch (Exception e) {
            logger.error("Failed to update tag: {}", tagPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to update tag: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public ActionResult delete(DeleteResourceAction action, AuthContext auth) {
        String correlationId = action.getCorrelationId();
        String tagPath = action.getResourcePath();

        logger.debug("Deleting tag: {} (correlationId={})", tagPath, correlationId);

        // Check tag exists
        if (!tagExists(tagPath)) {
            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.FAILURE)
                    .message("Tag not found: " + tagPath)
                    .errors(Collections.singletonList("NOT_FOUND"))
                    .build();
        }

        // Check for children if not recursive
        boolean hasChildren = hasChildren(tagPath);
        if (hasChildren && !action.isRecursive()) {
            return ActionResult.failure(correlationId,
                    "Tag has children. Use recursive=true to delete folder, or delete children first.",
                    Collections.singletonList("HAS_CHILDREN"));
        }

        // Require force=true for delete (safety)
        if (!action.getOptions().isForce()) {
            Map<String, Object> confirmData = new LinkedHashMap<>();
            confirmData.put("tagPath", tagPath);
            confirmData.put("hasChildren", hasChildren);

            return ActionResult.builder(correlationId)
                    .status(ActionResult.Status.PENDING_CONFIRMATION)
                    .message("Delete tag '" + tagPath + "'? This cannot be undone. Set force=true to confirm.")
                    .data(confirmData)
                    .build();
        }

        // Handle dry run
        if (action.getOptions().isDryRun()) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("tagPath", tagPath);
            preview.put("recursive", action.isRecursive());
            preview.put("hasChildren", hasChildren);

            return ActionResult.dryRun(correlationId,
                    "Would delete tag: " + tagPath,
                    preview);
        }

        try {
            deleteTagInternal(tagPath, action.isRecursive());

            logger.info("Deleted tag: {} by user {} (recursive={})",
                    tagPath, auth.getUserId(), action.isRecursive());

            return ActionResult.success(correlationId,
                    "Tag deleted successfully: " + tagPath,
                    Map.of("tagPath", tagPath, "deleted", true));

        } catch (Exception e) {
            logger.error("Failed to delete tag: {}", tagPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to delete tag: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    // ===== Internal helper methods =====

    /**
     * Gets the tag manager from the gateway context.
     */
    private GatewayTagManager getTagManager() {
        return gatewayContext.getTagManager();
    }

    /**
     * Gets a tag provider by name.
     */
    private TagProvider getTagProvider(String providerName) {
        GatewayTagManager tagManager = getTagManager();
        return tagManager.getTagProvider(providerName);
    }

    /**
     * Lists all available tag provider names.
     */
    private List<String> listProviderNames() {
        List<String> names = new ArrayList<>();
        GatewayTagManager tagManager = getTagManager();
        Collection<TagProvider> providers = tagManager.getTagProviders();
        for (TagProvider provider : providers) {
            names.add(provider.getName());
        }
        return names;
    }

    /**
     * Parses a tag path string into provider name and tag path components.
     */
    private ParsedTagPath parseTagPath(String pathStr) {
        // Handle provider-only paths like "[default]" or "[Sample_Tags]"
        if (pathStr.startsWith("[") && pathStr.endsWith("]")) {
            String providerName = pathStr.substring(1, pathStr.length() - 1);
            return new ParsedTagPath(providerName, null);
        }

        // Parse full tag path like "[default]Folder/Tag"
        try {
            TagPath tagPath = TagPathParser.parse(pathStr);
            return new ParsedTagPath(tagPath.getSource(), tagPath);
        } catch (Exception e) {
            logger.warn("Failed to parse tag path: {}", pathStr, e);
            // Try to extract provider manually
            if (pathStr.startsWith("[")) {
                int closeBracket = pathStr.indexOf(']');
                if (closeBracket > 0) {
                    String providerName = pathStr.substring(1, closeBracket);
                    String remainingPath = pathStr.substring(closeBracket + 1);
                    if (remainingPath.isEmpty()) {
                        return new ParsedTagPath(providerName, null);
                    }
                }
            }
            throw new IllegalArgumentException("Invalid tag path: " + pathStr);
        }
    }

    /**
     * Helper class for parsed tag paths.
     */
    private static class ParsedTagPath {
        final String providerName;
        final TagPath tagPath;

        ParsedTagPath(String providerName, TagPath tagPath) {
            this.providerName = providerName != null ? providerName : "default";
            this.tagPath = tagPath;
        }
    }

    /**
     * Checks if a tag exists at the given path.
     */
    private boolean tagExists(String tagPathStr) {
        logger.debug("Checking if tag exists: {}", tagPathStr);

        try {
            ParsedTagPath parsed = parseTagPath(tagPathStr);
            TagProvider provider = getTagProvider(parsed.providerName);

            if (provider == null) {
                logger.debug("Provider not found: {}", parsed.providerName);
                return false;
            }

            if (parsed.tagPath == null) {
                // Provider-only path - provider exists
                return true;
            }

            // Browse to check if tag exists by browsing the parent and looking for the tag
            Results<NodeDescription> results = provider.browseAsync(
                    parsed.tagPath, null
            ).get(10, TimeUnit.SECONDS);

            // If we got results without error, the path exists
            return results != null && results.getResultQuality().isGood();

        } catch (Exception e) {
            logger.debug("Error checking tag existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a tag has children (is a folder with contents).
     */
    private boolean hasChildren(String tagPathStr) {
        try {
            ParsedTagPath parsed = parseTagPath(tagPathStr);
            TagProvider provider = getTagProvider(parsed.providerName);

            if (provider == null) {
                return false;
            }

            TagPath browsePath = parsed.tagPath;
            Results<NodeDescription> results = provider.browseAsync(
                    browsePath, null
            ).get(10, TimeUnit.SECONDS);

            return results != null && !results.getResults().isEmpty();

        } catch (Exception e) {
            logger.debug("Error checking for children: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a tag using Ignition's TagManager.
     */
    private Map<String, Object> createTagInternal(String tagPathStr, Map<String, Object> config) throws Exception {
        ParsedTagPath parsed = parseTagPath(tagPathStr);
        TagProvider provider = getTagProvider(parsed.providerName);

        if (provider == null) {
            throw new IllegalArgumentException("Tag provider not found: " + parsed.providerName);
        }

        // Extract just the tag name portion (without provider brackets)
        String relativePath = tagPathStr;
        if (relativePath.startsWith("[")) {
            int closeBracket = relativePath.indexOf(']');
            if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                relativePath = relativePath.substring(closeBracket + 1);
            }
        }

        TagPath tagPath = TagPathParser.parse(relativePath);

        // Create the tag configuration
        TagConfiguration tagConfig = BasicTagConfiguration.createNew(tagPath);

        // Set the tag type (default to AtomicTag)
        String tagTypeStr = (String) config.getOrDefault("tagType", "AtomicTag");
        TagObjectType tagType = parseTagObjectType(tagTypeStr);
        tagConfig.setType(tagType);

        // For AtomicTags, set as memory tag and configure data type
        if (tagType == TagObjectType.AtomicTag) {
            tagConfig.set(WellKnownTagProps.ValueSource, "memory");

            // Set data type
            String dataTypeStr = (String) config.getOrDefault("dataType", "Int4");
            DataType dataType = parseDataType(dataTypeStr);
            tagConfig.set(WellKnownTagProps.DataType, dataType);

            // Set initial value if provided
            if (config.containsKey("value")) {
                Object value = config.get("value");
                QualifiedValue qv = new BasicQualifiedValue(value);
                tagConfig.set(WellKnownTagProps.Value, qv);
            }
        }

        // Set documentation if provided
        if (config.containsKey("documentation")) {
            tagConfig.set(WellKnownTagProps.Documentation, config.get("documentation").toString());
        }

        // Set name if provided (usually derived from path)
        if (config.containsKey("name")) {
            tagConfig.set(WellKnownTagProps.Name, config.get("name").toString());
        }

        // Save the tag
        logger.info("Saving tag configuration: {} to provider {}", relativePath, parsed.providerName);
        List<QualityCode> results = provider.saveTagConfigsAsync(
                Arrays.asList(tagConfig),
                CollisionPolicy.Abort
        ).get(30, TimeUnit.SECONDS);

        // Check result
        if (results.isEmpty()) {
            throw new RuntimeException("No result returned from saveTagConfigsAsync");
        }

        QualityCode result = results.get(0);
        if (result.isNotGood()) {
            throw new RuntimeException("Failed to create tag: " + result.toString());
        }

        logger.info("Successfully created tag: {}", tagPathStr);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tagPath", tagPathStr);
        response.put("status", "created");
        response.put("configuration", config);
        response.put("timestamp", Instant.now().toString());

        return response;
    }

    /**
     * Parses a tag object type string to TagObjectType enum.
     */
    private TagObjectType parseTagObjectType(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) {
            return TagObjectType.AtomicTag;
        }
        switch (typeStr.toLowerCase()) {
            case "folder":
                return TagObjectType.Folder;
            case "udttype":
            case "udt_type":
                return TagObjectType.UdtType;
            case "udtinstance":
            case "udt_instance":
                return TagObjectType.UdtInstance;
            case "atomictag":
            case "atomic_tag":
            default:
                return TagObjectType.AtomicTag;
        }
    }

    /**
     * Parses a data type string to DataType enum.
     */
    private DataType parseDataType(String dataTypeStr) {
        if (dataTypeStr == null || dataTypeStr.isEmpty()) {
            return DataType.Int4;
        }
        switch (dataTypeStr.toLowerCase()) {
            case "boolean":
            case "bool":
                return DataType.Boolean;
            case "int1":
            case "byte":
                return DataType.Int1;
            case "int2":
            case "short":
                return DataType.Int2;
            case "int4":
            case "int":
            case "integer":
                return DataType.Int4;
            case "int8":
            case "long":
                return DataType.Int8;
            case "float4":
            case "float":
                return DataType.Float4;
            case "float8":
            case "double":
                return DataType.Float8;
            case "string":
            case "text":
                return DataType.String;
            case "datetime":
            case "date":
                return DataType.DateTime;
            default:
                // Try to match by name
                try {
                    return DataType.valueOf(dataTypeStr);
                } catch (IllegalArgumentException e) {
                    return DataType.Int4;
                }
        }
    }

    /**
     * Reads tag configuration from Ignition.
     */
    private Map<String, Object> readTagInternal(String tagPathStr) throws Exception {
        ParsedTagPath parsed = parseTagPath(tagPathStr);
        TagProvider provider = getTagProvider(parsed.providerName);

        if (provider == null) {
            throw new IllegalArgumentException("Tag provider not found: " + parsed.providerName);
        }

        // Extract relative path (without provider brackets)
        String relativePath = tagPathStr;
        if (relativePath.startsWith("[")) {
            int closeBracket = relativePath.indexOf(']');
            if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                relativePath = relativePath.substring(closeBracket + 1);
            }
        }

        TagPath tagPath = TagPathParser.parse(relativePath);

        // Get tag configuration
        List<TagConfigurationModel> configs = provider.getTagConfigsAsync(
                Arrays.asList(tagPath), false, true
        ).get(30, TimeUnit.SECONDS);

        if (configs.isEmpty()) {
            throw new RuntimeException("Tag not found: " + tagPathStr);
        }

        TagConfigurationModel tagConfig = configs.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tagPath", tagPathStr);

        // Get tag type
        TagObjectType tagType = tagConfig.getType();
        if (tagType != null) {
            result.put("tagType", tagType.name());
        }

        // Get data type
        DataType dataType = tagConfig.get(WellKnownTagProps.DataType);
        if (dataType != null) {
            result.put("dataType", dataType.name());
        }

        // Get enabled state
        Boolean enabled = tagConfig.get(WellKnownTagProps.Enabled);
        result.put("enabled", enabled != null ? enabled : true);

        // Get documentation
        String documentation = tagConfig.get(WellKnownTagProps.Documentation);
        if (documentation != null && !documentation.isEmpty()) {
            result.put("documentation", documentation);
        }

        // Get value source
        String valueSource = tagConfig.get(WellKnownTagProps.ValueSource);
        if (valueSource != null) {
            result.put("valueSource", valueSource);
        }

        return result;
    }

    /**
     * Reads the current value of a tag.
     */
    private Map<String, Object> readTagValue(String tagPathStr) {
        try {
            ParsedTagPath parsed = parseTagPath(tagPathStr);
            TagProvider provider = getTagProvider(parsed.providerName);

            if (provider == null) {
                Map<String, Object> valueInfo = new LinkedHashMap<>();
                valueInfo.put("error", "Provider not found: " + parsed.providerName);
                return valueInfo;
            }

            // Extract relative path (without provider brackets)
            String relativePath = tagPathStr;
            if (relativePath.startsWith("[")) {
                int closeBracket = relativePath.indexOf(']');
                if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                    relativePath = relativePath.substring(closeBracket + 1);
                }
            }

            TagPath tagPath = TagPathParser.parse(relativePath);

            List<QualifiedValue> values = provider.readAsync(
                    Arrays.asList(tagPath),
                    SecurityContext.emptyContext()
            ).get(30, TimeUnit.SECONDS);

            if (values.isEmpty()) {
                Map<String, Object> valueInfo = new LinkedHashMap<>();
                valueInfo.put("error", "No value returned");
                return valueInfo;
            }

            QualifiedValue qv = values.get(0);

            Map<String, Object> valueInfo = new LinkedHashMap<>();
            valueInfo.put("value", qv.getValue());
            valueInfo.put("quality", qv.getQuality().toString());
            valueInfo.put("timestamp", qv.getTimestamp() != null ?
                    qv.getTimestamp().toString() : Instant.now().toString());

            return valueInfo;

        } catch (Exception e) {
            logger.error("Failed to read tag value: {}", e.getMessage(), e);
            Map<String, Object> valueInfo = new LinkedHashMap<>();
            valueInfo.put("error", e.getMessage());
            return valueInfo;
        }
    }

    /**
     * Updates a tag configuration.
     */
    private Map<String, Object> updateTagInternal(String tagPathStr, Map<String, Object> config, boolean merge) throws Exception {
        ParsedTagPath parsed = parseTagPath(tagPathStr);
        TagProvider provider = getTagProvider(parsed.providerName);

        if (provider == null) {
            throw new IllegalArgumentException("Tag provider not found: " + parsed.providerName);
        }

        // Extract relative path (without provider brackets)
        String relativePath = tagPathStr;
        if (relativePath.startsWith("[")) {
            int closeBracket = relativePath.indexOf(']');
            if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                relativePath = relativePath.substring(closeBracket + 1);
            }
        }

        TagPath tagPath = TagPathParser.parse(relativePath);

        // Get current tag configuration
        List<TagConfigurationModel> configs = provider.getTagConfigsAsync(
                Arrays.asList(tagPath), false, true
        ).get(30, TimeUnit.SECONDS);

        if (configs.isEmpty()) {
            throw new RuntimeException("Tag not found: " + tagPathStr);
        }

        TagConfigurationModel tagConfig = configs.get(0);

        // Update properties based on config map
        if (config.containsKey("dataType")) {
            DataType dataType = parseDataType(config.get("dataType").toString());
            tagConfig.set(WellKnownTagProps.DataType, dataType);
        }

        if (config.containsKey("documentation")) {
            tagConfig.set(WellKnownTagProps.Documentation, config.get("documentation").toString());
        }

        if (config.containsKey("enabled")) {
            Object enabledValue = config.get("enabled");
            Boolean enabled = enabledValue instanceof Boolean ?
                    (Boolean) enabledValue :
                    Boolean.parseBoolean(enabledValue.toString());
            tagConfig.set(WellKnownTagProps.Enabled, enabled);
        }

        // Save the updated configuration
        CollisionPolicy policy = merge ? CollisionPolicy.MergeOverwrite : CollisionPolicy.Overwrite;
        List<QualityCode> results = provider.saveTagConfigsAsync(
                Arrays.asList(tagConfig),
                policy
        ).get(30, TimeUnit.SECONDS);

        if (results.isEmpty()) {
            throw new RuntimeException("No result from update operation");
        }

        QualityCode result = results.get(0);
        if (result.isNotGood()) {
            throw new RuntimeException("Failed to update tag: " + result.toString());
        }

        logger.info("Successfully updated tag: {}", tagPathStr);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tagPath", tagPathStr);
        response.put("status", "updated");
        response.put("merge", merge);
        response.put("changes", config);
        response.put("timestamp", Instant.now().toString());

        return response;
    }

    /**
     * Writes a value to a tag.
     */
    private ActionResult writeTagValue(String correlationId, String tagPathStr, Object value, boolean dryRun) {
        if (dryRun) {
            return ActionResult.dryRun(correlationId,
                    "Would write value to tag: " + tagPathStr,
                    Map.of("tagPath", tagPathStr, "value", value));
        }

        try {
            ParsedTagPath parsed = parseTagPath(tagPathStr);
            TagProvider provider = getTagProvider(parsed.providerName);

            if (provider == null) {
                return ActionResult.failure(correlationId,
                        "Tag provider not found: " + parsed.providerName,
                        Collections.singletonList("PROVIDER_NOT_FOUND"));
            }

            // Extract relative path (without provider brackets)
            String relativePath = tagPathStr;
            if (relativePath.startsWith("[")) {
                int closeBracket = relativePath.indexOf(']');
                if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                    relativePath = relativePath.substring(closeBracket + 1);
                }
            }

            TagPath tagPath = TagPathParser.parse(relativePath);
            QualifiedValue qualifiedValue = new BasicQualifiedValue(value);

            logger.info("Writing value {} to tag {}", value, tagPathStr);

            List<QualityCode> results = provider.writeAsync(
                    Arrays.asList(tagPath),
                    Arrays.asList(qualifiedValue),
                    SecurityContext.emptyContext()
            ).get(30, TimeUnit.SECONDS);

            if (results.isEmpty()) {
                return ActionResult.failure(correlationId,
                        "No result from write operation",
                        Collections.singletonList("EMPTY_RESULT"));
            }

            QualityCode result = results.get(0);
            if (result.isNotGood()) {
                return ActionResult.failure(correlationId,
                        "Failed to write tag value: " + result.toString(),
                        Collections.singletonList(result.toString()));
            }

            logger.info("Successfully wrote value {} to tag {}", value, tagPathStr);

            return ActionResult.success(correlationId,
                    "Tag value written successfully",
                    Map.of(
                            "tagPath", tagPathStr,
                            "value", value,
                            "timestamp", Instant.now().toString()
                    ));

        } catch (Exception e) {
            logger.error("Failed to write tag value: {}", e.getMessage(), e);
            return ActionResult.failure(correlationId,
                    "Failed to write tag value: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Deletes a tag.
     */
    private void deleteTagInternal(String tagPathStr, boolean recursive) throws Exception {
        ParsedTagPath parsed = parseTagPath(tagPathStr);
        TagProvider provider = getTagProvider(parsed.providerName);

        if (provider == null) {
            throw new IllegalArgumentException("Tag provider not found: " + parsed.providerName);
        }

        // Extract relative path (without provider brackets)
        String relativePath = tagPathStr;
        if (relativePath.startsWith("[")) {
            int closeBracket = relativePath.indexOf(']');
            if (closeBracket > 0 && closeBracket < relativePath.length() - 1) {
                relativePath = relativePath.substring(closeBracket + 1);
            }
        }

        TagPath tagPath = TagPathParser.parse(relativePath);

        logger.info("Deleting tag: {} (recursive={})", tagPathStr, recursive);

        List<QualityCode> results = provider.removeTagConfigsAsync(
                Arrays.asList(tagPath)
        ).get(30, TimeUnit.SECONDS);

        if (results.isEmpty()) {
            throw new RuntimeException("No result from delete operation");
        }

        QualityCode result = results.get(0);
        if (result.isNotGood()) {
            throw new RuntimeException("Failed to delete tag: " + result.toString());
        }

        logger.info("Successfully deleted tag: {}", tagPathStr);
    }

    /**
     * Browses a tag folder and returns its contents.
     */
    private ActionResult browseTagFolder(String correlationId, String folderPath) {
        logger.debug("Browsing tag folder: {}", folderPath);

        try {
            // Handle empty path - list all providers
            if (folderPath == null || folderPath.isEmpty()) {
                return listAllProviders(correlationId);
            }

            ParsedTagPath parsed = parseTagPath(folderPath);
            TagProvider provider = getTagProvider(parsed.providerName);

            if (provider == null) {
                List<String> availableProviders = listProviderNames();
                return ActionResult.builder(correlationId)
                        .status(ActionResult.Status.FAILURE)
                        .message("Tag provider not found: " + parsed.providerName)
                        .errors(Collections.singletonList("PROVIDER_NOT_FOUND"))
                        .data(Map.of("availableProviders", availableProviders))
                        .build();
            }

            // Browse the tag path
            TagPath browsePath = parsed.tagPath;
            Results<NodeDescription> results = provider.browseAsync(
                    browsePath, null
            ).get(10, TimeUnit.SECONDS);

            List<Map<String, Object>> children = new ArrayList<>();

            if (results != null && results.getResults() != null) {
                for (NodeDescription node : results.getResults()) {
                    Map<String, Object> tagInfo = new LinkedHashMap<>();
                    tagInfo.put("name", node.getName());
                    // Build path - avoid duplicate provider prefix
                    String fullPath = node.getFullPath().toString();
                    if (!fullPath.startsWith("[")) {
                        fullPath = "[" + parsed.providerName + "]" + fullPath;
                    }
                    tagInfo.put("path", fullPath);
                    tagInfo.put("type", node.getObjectType() != null ?
                            node.getObjectType().name() : "Unknown");
                    tagInfo.put("hasChildren", node.hasChildren());

                    // Add data type if available
                    DataType dataType = node.getDataType();
                    if (dataType != null) {
                        tagInfo.put("dataType", dataType.name());
                    }

                    children.add(tagInfo);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", parsed.providerName);
            result.put("folderPath", folderPath);
            result.put("children", children);
            result.put("childCount", children.size());

            return ActionResult.success(correlationId, "Folder browsed successfully", result);

        } catch (Exception e) {
            logger.error("Failed to browse tag folder: {}", folderPath, e);
            return ActionResult.failure(correlationId,
                    "Failed to browse tags: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Lists all available tag providers.
     */
    private ActionResult listAllProviders(String correlationId) {
        logger.debug("Listing all tag providers");

        try {
            GatewayTagManager tagManager = getTagManager();
            Collection<TagProvider> providers = tagManager.getTagProviders();

            List<Map<String, Object>> providerList = new ArrayList<>();
            for (TagProvider provider : providers) {
                Map<String, Object> providerInfo = new LinkedHashMap<>();
                providerInfo.put("name", provider.getName());
                providerInfo.put("path", "[" + provider.getName() + "]");

                // Try to get status info safely
                try {
                    var statusFuture = provider.getStatusInformation();
                    if (statusFuture != null) {
                        var status = statusFuture.get(5, TimeUnit.SECONDS);
                        if (status != null) {
                            providerInfo.put("status", "Running");
                        } else {
                            providerInfo.put("status", "Unknown");
                        }
                    }
                } catch (Exception e) {
                    providerInfo.put("status", "Active");
                }

                providerList.add(providerInfo);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("providers", providerList);
            result.put("providerCount", providerList.size());

            return ActionResult.success(correlationId, "Tag providers listed successfully", result);

        } catch (Exception e) {
            logger.error("Failed to list tag providers", e);
            return ActionResult.failure(correlationId,
                    "Failed to list tag providers: " + e.getMessage(),
                    Collections.singletonList(e.getMessage()));
        }
    }

    /**
     * Checks if the read action should include current value.
     */
    private boolean shouldIncludeValue(ReadResourceAction action) {
        // Check if fields includes "value" or if explicit option is set
        if (action.getFields() != null && action.getFields().contains("value")) {
            return true;
        }
        return action.isIncludeChildren(); // Use includeChildren as proxy for now
    }

    /**
     * Builds a preview of what would be created.
     */
    private Map<String, Object> buildTagPreview(String tagPath, Map<String, Object> config) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("tagPath", tagPath);
        preview.put("configuration", config);
        preview.put("action", "CREATE");

        return preview;
    }

    /**
     * Builds a validation failure result.
     */
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
