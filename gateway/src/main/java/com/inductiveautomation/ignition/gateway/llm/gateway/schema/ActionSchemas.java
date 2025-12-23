package com.inductiveautomation.ignition.gateway.llm.gateway.schema;

import com.inductiveautomation.ignition.gateway.llm.gateway.auth.Permission;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines JSON schemas for all LLM-callable tools.
 * These schemas tell the LLM what parameters are available for each action.
 */
public class ActionSchemas {

    private static final Map<String, Map<String, Object>> SCHEMAS = new HashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

    static {
        // Tag operations
        SCHEMAS.put("create_tag", buildCreateTagSchema());
        DESCRIPTIONS.put("create_tag", "Create a new tag in the Ignition tag provider. " +
                "Required fields depend on tagType: AtomicTag needs dataType, OpcTag needs opcItemPath, " +
                "ExpressionTag needs expression, UdtInstance needs typeId. " +
                "Path format: [provider]path/to/NewTag");

        SCHEMAS.put("read_tag", buildReadTagSchema());
        DESCRIPTIONS.put("read_tag", "Read tag configuration and/or current value. " +
                "Path format: [provider]path/to/tag. Examples: [default]Folder/Tag, [Sample_Tags]Realistic/Realistic0");

        SCHEMAS.put("update_tag", buildUpdateTagSchema());
        DESCRIPTIONS.put("update_tag", "Update tag configuration or properties");

        SCHEMAS.put("delete_tag", buildDeleteTagSchema());
        DESCRIPTIONS.put("delete_tag", "Delete a tag (requires confirmation)");

        SCHEMAS.put("write_tag_value", buildWriteTagValueSchema());
        DESCRIPTIONS.put("write_tag_value", "Write a value to an existing tag");

        SCHEMAS.put("list_tags", buildListTagsSchema());
        DESCRIPTIONS.put("list_tags", "List tags in a folder or list all tag providers. " +
                "Use parentPath: \"*\" to list ALL providers (default, Sample_Tags, System, etc.). " +
                "Use parentPath: \"[default]\" to browse the default provider root. " +
                "Use parentPath: \"[Sample_Tags]Realistic\" to browse a specific folder.");

        // View operations
        SCHEMAS.put("create_view", buildCreateViewSchema());
        DESCRIPTIONS.put("create_view", "Create a new Perspective view");

        SCHEMAS.put("read_view", buildReadViewSchema());
        DESCRIPTIONS.put("read_view", "Read Perspective view configuration");

        SCHEMAS.put("update_view", buildUpdateViewSchema());
        DESCRIPTIONS.put("update_view", "Update a Perspective view");

        SCHEMAS.put("delete_view", buildDeleteViewSchema());
        DESCRIPTIONS.put("delete_view", "Delete a Perspective view (requires confirmation)");

        SCHEMAS.put("list_views", buildListViewsSchema());
        DESCRIPTIONS.put("list_views", "List Perspective views in a project");

        // Project operations
        SCHEMAS.put("list_projects", buildListProjectsSchema());
        DESCRIPTIONS.put("list_projects", "List all available projects");

        // Script operations
        SCHEMAS.put("create_script", buildCreateScriptSchema());
        DESCRIPTIONS.put("create_script", "Create a new project library script. " +
                "Path format: {projectName}/library/{scriptPath}. " +
                "Use scriptType='library' for project library scripts.");

        SCHEMAS.put("read_script", buildReadScriptSchema());
        DESCRIPTIONS.put("read_script", "Read a project library script's content");

        SCHEMAS.put("update_script", buildUpdateScriptSchema());
        DESCRIPTIONS.put("update_script", "Update a project library script");

        SCHEMAS.put("delete_script", buildDeleteScriptSchema());
        DESCRIPTIONS.put("delete_script", "Delete a project library script (requires confirmation)");

        SCHEMAS.put("list_scripts", buildListScriptsSchema());
        DESCRIPTIONS.put("list_scripts", "List project library scripts");

        // Named Query operations
        SCHEMAS.put("create_named_query", buildCreateNamedQuerySchema());
        DESCRIPTIONS.put("create_named_query", "Create a new named query. " +
                "Valid queryType values: Query (for SELECT), Update, Insert, Delete, Scalar");

        SCHEMAS.put("read_named_query", buildReadNamedQuerySchema());
        DESCRIPTIONS.put("read_named_query", "Read a named query configuration and SQL");

        SCHEMAS.put("update_named_query", buildUpdateNamedQuerySchema());
        DESCRIPTIONS.put("update_named_query", "Update a named query");

        SCHEMAS.put("delete_named_query", buildDeleteNamedQuerySchema());
        DESCRIPTIONS.put("delete_named_query", "Delete a named query (requires confirmation)");

        SCHEMAS.put("list_named_queries", buildListNamedQueriesSchema());
        DESCRIPTIONS.put("list_named_queries", "List named queries in a project");
    }

    // ==================== Tag Schemas ====================

    private static Map<String, Object> buildCreateTagSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("tagPath", stringProp(
                "Full tag path including provider, e.g., '[default]Folder/TagName'",
                true));

        properties.put("tagType", enumProp(
                "Tag type",
                false,
                "AtomicTag",
                Arrays.asList("AtomicTag", "UdtInstance", "Folder", "ExpressionTag",
                        "QueryTag", "OpcTag", "DerivedTag", "ReferenceTag")));

        properties.put("dataType", enumProp(
                "Data type for the tag value",
                false,
                "Int4",
                Arrays.asList("Int1", "Int2", "Int4", "Int8", "Float4", "Float8",
                        "Boolean", "String", "DateTime", "DataSet")));

        properties.put("value", anyProp("Initial value for the tag"));

        properties.put("engUnit", stringProp(
                "Engineering unit (e.g., 'PSI', '°F', 'GPM')", false));

        properties.put("documentation", stringProp(
                "Tag documentation/description", false));

        properties.put("opcItemPath", stringProp(
                "OPC item path for OpcTag type (e.g., '[Server]ns=1;s=Path/To/Item')", false));

        properties.put("expression", stringProp(
                "Expression for ExpressionTag type (e.g., '{[.]ParentTag} * 2')", false));

        properties.put("typeId", stringProp(
                "UDT type ID for UdtInstance type", false));

        properties.put("dryRun", booleanProp(
                "If true, validate without creating", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("tagPath"));

        return schema;
    }

    private static Map<String, Object> buildReadTagSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("tagPath", stringProp(
                "Full tag path to read (e.g., '[default]Folder/TagName')", true));

        properties.put("includeValue", booleanProp(
                "Include current tag value in response", false, true));

        properties.put("includeConfig", booleanProp(
                "Include full tag configuration", false, true));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("tagPath"));

        return schema;
    }

    private static Map<String, Object> buildUpdateTagSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("tagPath", stringProp(
                "Full tag path to update", true));

        properties.put("changes", objectProp(
                "Changes to apply (only specified fields are updated)"));

        properties.put("documentation", stringProp(
                "Update tag documentation", false));

        properties.put("engUnit", stringProp(
                "Update engineering unit", false));

        properties.put("expression", stringProp(
                "Update expression (for ExpressionTag)", false));

        properties.put("comment", stringProp(
                "Comment describing the change (for audit)", false));

        properties.put("dryRun", booleanProp(
                "If true, validate and preview without saving", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("tagPath"));

        return schema;
    }

    private static Map<String, Object> buildDeleteTagSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("tagPath", stringProp(
                "Full tag path to delete", true));

        properties.put("recursive", booleanProp(
                "Delete folder contents recursively", false, false));

        properties.put("force", booleanProp(
                "Skip confirmation (use with caution)", false, false));

        properties.put("dryRun", booleanProp(
                "If true, preview without deleting", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("tagPath"));

        return schema;
    }

    private static Map<String, Object> buildWriteTagValueSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("tagPath", stringProp(
                "Full tag path to write to", true));

        properties.put("value", anyProp("Value to write"));

        properties.put("comment", stringProp(
                "Comment for audit log", false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("tagPath", "value"));

        return schema;
    }

    private static Map<String, Object> buildListTagsSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("parentPath", stringProp(
                "Tag path to browse. Use '*' to list ALL providers. " +
                "Use '[default]' to browse default provider root. " +
                "Use '[Sample_Tags]Realistic' to browse a folder in Sample_Tags provider. " +
                "NOTE: Provider name is always in brackets at the START.", true));

        properties.put("recursive", booleanProp(
                "Include nested folders", false, false));

        properties.put("filter", stringProp(
                "Filter pattern (supports wildcards)", false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("parentPath"));

        return schema;
    }

    // ==================== View Schemas ====================

    private static Map<String, Object> buildCreateViewSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Perspective project", true));

        properties.put("viewPath", stringProp(
                "Path for the view within the project (e.g., 'Screens/Main')", true));

        properties.put("root", objectProp(
                "Root component configuration"));

        properties.put("params", objectProp(
                "View parameter definitions"));

        properties.put("dryRun", booleanProp(
                "If true, validate without creating", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "viewPath"));

        return schema;
    }

    private static Map<String, Object> buildReadViewSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Perspective project", true));

        properties.put("viewPath", stringProp(
                "Path to the view", true));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "viewPath"));

        return schema;
    }

    private static Map<String, Object> buildUpdateViewSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Perspective project", true));

        properties.put("viewPath", stringProp(
                "Path to the view", true));

        properties.put("changes", objectProp(
                "Changes to merge into the view"));

        properties.put("jsonPatch", arrayProp(
                "RFC 6902 JSON Patch operations for fine-grained updates"));

        properties.put("comment", stringProp(
                "Comment describing the change (for audit)", false));

        properties.put("dryRun", booleanProp(
                "If true, validate and preview without saving", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "viewPath"));

        return schema;
    }

    private static Map<String, Object> buildDeleteViewSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Perspective project", true));

        properties.put("viewPath", stringProp(
                "Path to the view", true));

        properties.put("force", booleanProp(
                "Skip confirmation (use with caution)", false, false));

        properties.put("dryRun", booleanProp(
                "If true, preview without deleting", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "viewPath"));

        return schema;
    }

    private static Map<String, Object> buildListViewsSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Perspective project", true));

        properties.put("parentPath", stringProp(
                "Parent folder path within the project", false));

        properties.put("recursive", booleanProp(
                "Include nested folders", false, true));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName"));

        return schema;
    }

    // ==================== Project Schemas ====================

    private static Map<String, Object> buildListProjectsSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("includeDisabled", booleanProp(
                "Include disabled projects", false, false));

        schema.put("properties", properties);
        schema.put("required", new ArrayList<String>());

        return schema;
    }

    // ==================== Script Schemas ====================

    private static Map<String, Object> buildCreateScriptSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project (use the folder name, not display name)", true));

        properties.put("scriptType", enumProp(
                "Type of script location",
                true,
                "library",
                Arrays.asList("library")));

        properties.put("scriptPath", stringProp(
                "Path within the script type (e.g., 'MyModule/utils' creates MyModule/utils.py)", true));

        properties.put("code", stringProp(
                "Python script content", true));

        properties.put("documentation", stringProp(
                "Script documentation/description", false));

        properties.put("acknowledgeWarnings", booleanProp(
                "Acknowledge security warnings (for potentially dangerous code)", false, false));

        properties.put("dryRun", booleanProp(
                "If true, validate without creating", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "scriptType", "scriptPath", "code"));

        return schema;
    }

    private static Map<String, Object> buildReadScriptSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("scriptType", enumProp(
                "Type of script location",
                true,
                "library",
                Arrays.asList("library")));

        properties.put("scriptPath", stringProp(
                "Path to the script (e.g., 'MyModule/utils')", true));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "scriptType", "scriptPath"));

        return schema;
    }

    private static Map<String, Object> buildUpdateScriptSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("scriptType", enumProp(
                "Type of script location",
                true,
                "library",
                Arrays.asList("library")));

        properties.put("scriptPath", stringProp(
                "Path to the script", true));

        properties.put("code", stringProp(
                "Updated Python script content", true));

        properties.put("documentation", stringProp(
                "Updated script documentation", false));

        properties.put("acknowledgeWarnings", booleanProp(
                "Acknowledge security warnings", false, false));

        properties.put("comment", stringProp(
                "Comment describing the change (for audit)", false));

        properties.put("dryRun", booleanProp(
                "If true, validate without saving", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "scriptType", "scriptPath", "code"));

        return schema;
    }

    private static Map<String, Object> buildDeleteScriptSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("scriptType", enumProp(
                "Type of script location",
                true,
                "library",
                Arrays.asList("library")));

        properties.put("scriptPath", stringProp(
                "Path to the script to delete", true));

        properties.put("force", booleanProp(
                "Skip confirmation (use with caution)", false, false));

        properties.put("dryRun", booleanProp(
                "If true, preview without deleting", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "scriptType", "scriptPath"));

        return schema;
    }

    private static Map<String, Object> buildListScriptsSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("scriptType", enumProp(
                "Type of script location",
                true,
                "library",
                Arrays.asList("library")));

        properties.put("parentPath", stringProp(
                "Parent folder path to list (empty for root)", false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "scriptType"));

        return schema;
    }

    // ==================== Named Query Schemas ====================

    private static Map<String, Object> buildCreateNamedQuerySchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project (use the folder name, not display name)", true));

        properties.put("queryPath", stringProp(
                "Path for the named query (e.g., 'Alarms/GetActiveAlarms')", true));

        properties.put("queryType", enumProp(
                "Type of SQL query. Use 'Query' for SELECT statements.",
                true,
                "Query",
                Arrays.asList("Query", "Update", "Insert", "Delete", "Scalar")));

        properties.put("database", stringProp(
                "Database connection name", true));

        properties.put("query", stringProp(
                "SQL query text", true));

        properties.put("parameters", arrayProp(
                "Query parameters array with name, type, and optional default value"));

        properties.put("fallbackValue", anyProp(
                "Value to return if query fails"));

        properties.put("cacheEnabled", booleanProp(
                "Enable query result caching", false, false));

        properties.put("cacheExpiry", stringProp(
                "Cache expiry duration (e.g., '5 minutes')", false));

        properties.put("dryRun", booleanProp(
                "If true, validate without creating", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "queryPath", "queryType", "database", "query"));

        return schema;
    }

    private static Map<String, Object> buildReadNamedQuerySchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("queryPath", stringProp(
                "Path to the named query", true));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "queryPath"));

        return schema;
    }

    private static Map<String, Object> buildUpdateNamedQuerySchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("queryPath", stringProp(
                "Path to the named query", true));

        properties.put("queryType", enumProp(
                "Type of SQL query",
                false,
                null,
                Arrays.asList("Query", "Update", "Insert", "Delete", "Scalar")));

        properties.put("database", stringProp(
                "Database connection name", false));

        properties.put("query", stringProp(
                "Updated SQL query text", false));

        properties.put("parameters", arrayProp(
                "Updated query parameters array"));

        properties.put("fallbackValue", anyProp(
                "Value to return if query fails"));

        properties.put("cacheEnabled", booleanProp(
                "Enable query result caching", false, false));

        properties.put("cacheExpiry", stringProp(
                "Cache expiry duration", false));

        properties.put("comment", stringProp(
                "Comment describing the change (for audit)", false));

        properties.put("dryRun", booleanProp(
                "If true, validate without saving", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "queryPath"));

        return schema;
    }

    private static Map<String, Object> buildDeleteNamedQuerySchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("queryPath", stringProp(
                "Path to the named query to delete", true));

        properties.put("force", booleanProp(
                "Skip confirmation (use with caution)", false, false));

        properties.put("dryRun", booleanProp(
                "If true, preview without deleting", false, false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName", "queryPath"));

        return schema;
    }

    private static Map<String, Object> buildListNamedQueriesSchema() {
        Map<String, Object> schema = createBaseSchema("object");
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("projectName", stringProp(
                "Name of the Ignition project", true));

        properties.put("parentPath", stringProp(
                "Parent folder path (empty for root)", false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("projectName"));

        return schema;
    }

    // ==================== Schema Helpers ====================

    private static Map<String, Object> createBaseSchema(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        return schema;
    }

    private static Map<String, Object> stringProp(String description, boolean required) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    private static Map<String, Object> booleanProp(String description, boolean required, boolean defaultValue) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "boolean");
        prop.put("description", description);
        prop.put("default", defaultValue);
        return prop;
    }

    private static Map<String, Object> enumProp(String description, boolean required,
                                                 String defaultValue, List<String> values) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        prop.put("enum", values);
        if (defaultValue != null) {
            prop.put("default", defaultValue);
        }
        return prop;
    }

    private static Map<String, Object> anyProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("description", description);
        // No type constraint - accepts any JSON value
        return prop;
    }

    private static Map<String, Object> objectProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "object");
        prop.put("description", description);
        return prop;
    }

    private static Map<String, Object> arrayProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "array");
        prop.put("description", description);
        return prop;
    }

    // ==================== Public API ====================

    /**
     * Gets the schema for a specific tool.
     */
    public static Map<String, Object> getSchema(String toolName) {
        return SCHEMAS.get(toolName);
    }

    /**
     * Gets the description for a specific tool.
     */
    public static String getDescription(String toolName) {
        return DESCRIPTIONS.get(toolName);
    }

    /**
     * Gets all tool definitions.
     */
    public static List<LLMToolDefinition> getAllToolDefinitions() {
        List<LLMToolDefinition> tools = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : SCHEMAS.entrySet()) {
            String name = entry.getKey();
            tools.add(LLMToolDefinition.builder()
                    .name(name)
                    .description(DESCRIPTIONS.get(name))
                    .inputSchema(entry.getValue())
                    .build());
        }

        return tools;
    }

    /**
     * Gets tool definitions filtered by user permissions.
     */
    public static List<LLMToolDefinition> getToolsForPermissions(Set<Permission> permissions) {
        List<LLMToolDefinition> tools = new ArrayList<>();

        boolean isAdmin = permissions.contains(Permission.ADMIN);
        boolean canReadAll = permissions.contains(Permission.READ_ALL);

        // Tag tools
        if (isAdmin || permissions.contains(Permission.TAG_CREATE)) {
            tools.add(buildToolDef("create_tag"));
        }
        if (isAdmin || canReadAll || permissions.contains(Permission.TAG_READ)) {
            tools.add(buildToolDef("read_tag"));
            tools.add(buildToolDef("list_tags"));
        }
        if (isAdmin || permissions.contains(Permission.TAG_UPDATE)) {
            tools.add(buildToolDef("update_tag"));
        }
        if (isAdmin || permissions.contains(Permission.TAG_DELETE)) {
            tools.add(buildToolDef("delete_tag"));
        }
        if (isAdmin || permissions.contains(Permission.TAG_WRITE_VALUE)) {
            tools.add(buildToolDef("write_tag_value"));
        }

        // View tools
        if (isAdmin || permissions.contains(Permission.VIEW_CREATE)) {
            tools.add(buildToolDef("create_view"));
        }
        if (isAdmin || canReadAll || permissions.contains(Permission.VIEW_READ)) {
            tools.add(buildToolDef("read_view"));
            tools.add(buildToolDef("list_views"));
        }
        if (isAdmin || permissions.contains(Permission.VIEW_UPDATE)) {
            tools.add(buildToolDef("update_view"));
        }
        if (isAdmin || permissions.contains(Permission.VIEW_DELETE)) {
            tools.add(buildToolDef("delete_view"));
        }

        // Project tools
        if (isAdmin || canReadAll || permissions.contains(Permission.PROJECT_READ)) {
            tools.add(buildToolDef("list_projects"));
        }

        // Script tools
        if (isAdmin || permissions.contains(Permission.SCRIPT_CREATE)) {
            tools.add(buildToolDef("create_script"));
        }
        if (isAdmin || canReadAll || permissions.contains(Permission.SCRIPT_READ)) {
            tools.add(buildToolDef("read_script"));
            tools.add(buildToolDef("list_scripts"));
        }
        if (isAdmin || permissions.contains(Permission.SCRIPT_UPDATE)) {
            tools.add(buildToolDef("update_script"));
        }
        if (isAdmin || permissions.contains(Permission.SCRIPT_DELETE)) {
            tools.add(buildToolDef("delete_script"));
        }

        // Named Query tools
        if (isAdmin || permissions.contains(Permission.NAMED_QUERY_CREATE)) {
            tools.add(buildToolDef("create_named_query"));
        }
        if (isAdmin || canReadAll || permissions.contains(Permission.NAMED_QUERY_READ)) {
            tools.add(buildToolDef("read_named_query"));
            tools.add(buildToolDef("list_named_queries"));
        }
        if (isAdmin || permissions.contains(Permission.NAMED_QUERY_UPDATE)) {
            tools.add(buildToolDef("update_named_query"));
        }
        if (isAdmin || permissions.contains(Permission.NAMED_QUERY_DELETE)) {
            tools.add(buildToolDef("delete_named_query"));
        }

        return tools;
    }

    private static LLMToolDefinition buildToolDef(String name) {
        return LLMToolDefinition.builder()
                .name(name)
                .description(DESCRIPTIONS.get(name))
                .inputSchema(SCHEMAS.get(name))
                .build();
    }
}
