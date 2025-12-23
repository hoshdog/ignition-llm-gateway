package com.inductiveautomation.ignition.gateway.llm.gateway.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inductiveautomation.ignition.gateway.llm.actions.CreateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.DeleteResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.ReadResourceAction;
import com.inductiveautomation.ignition.gateway.llm.actions.UpdateResourceAction;
import com.inductiveautomation.ignition.gateway.llm.common.model.Action;
import com.inductiveautomation.ignition.gateway.llm.common.model.ActionOptions;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.LLMToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses LLM tool calls into typed Action objects.
 */
public class ActionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses an LLM tool call into an Action.
     */
    public static Action parseToolCall(LLMToolCall toolCall) throws ParseException {
        String toolName = toolCall.getName();
        JsonNode args;

        try {
            args = MAPPER.readTree(toolCall.getArguments());
        } catch (Exception e) {
            throw new ParseException("Invalid JSON in tool arguments: " + e.getMessage(), e);
        }

        // Route to appropriate parser based on tool name
        if ("create_tag".equals(toolName)) {
            return parseCreateTag(args);
        } else if ("read_tag".equals(toolName)) {
            return parseReadTag(args);
        } else if ("update_tag".equals(toolName)) {
            return parseUpdateTag(args);
        } else if ("delete_tag".equals(toolName)) {
            return parseDeleteTag(args);
        } else if ("write_tag_value".equals(toolName)) {
            return parseWriteTagValue(args);
        } else if ("list_tags".equals(toolName)) {
            return parseListTags(args);
        } else if ("create_view".equals(toolName)) {
            return parseCreateView(args);
        } else if ("read_view".equals(toolName)) {
            return parseReadView(args);
        } else if ("update_view".equals(toolName)) {
            return parseUpdateView(args);
        } else if ("delete_view".equals(toolName)) {
            return parseDeleteView(args);
        } else if ("list_views".equals(toolName)) {
            return parseListViews(args);
        } else if ("list_projects".equals(toolName)) {
            return parseListProjects(args);
        } else if ("read_project".equals(toolName)) {
            return parseReadProject(args);
        // Script tools
        } else if ("create_script".equals(toolName)) {
            return parseCreateScript(args);
        } else if ("read_script".equals(toolName)) {
            return parseReadScript(args);
        } else if ("update_script".equals(toolName)) {
            return parseUpdateScript(args);
        } else if ("delete_script".equals(toolName)) {
            return parseDeleteScript(args);
        } else if ("list_scripts".equals(toolName)) {
            return parseListScripts(args);
        // Named Query tools
        } else if ("create_named_query".equals(toolName)) {
            return parseCreateNamedQuery(args);
        } else if ("read_named_query".equals(toolName)) {
            return parseReadNamedQuery(args);
        } else if ("update_named_query".equals(toolName)) {
            return parseUpdateNamedQuery(args);
        } else if ("delete_named_query".equals(toolName)) {
            return parseDeleteNamedQuery(args);
        } else if ("list_named_queries".equals(toolName)) {
            return parseListNamedQueries(args);
        } else {
            throw new ParseException("Unknown tool: " + toolName);
        }
    }

    // ==================== Tag Parsers ====================

    private static CreateResourceAction parseCreateTag(JsonNode args) throws ParseException {
        String tagPath = getRequiredString(args, "tagPath");
        Map<String, Object> payload = new LinkedHashMap<>();

        // Extract tag configuration
        copyIfPresent(args, payload, "tagType");
        copyIfPresent(args, payload, "dataType");
        copyIfPresent(args, payload, "value");
        copyIfPresent(args, payload, "engUnit");
        copyIfPresent(args, payload, "documentation");
        copyIfPresent(args, payload, "opcItemPath");
        copyIfPresent(args, payload, "expression");
        copyIfPresent(args, payload, "typeId");

        // Extract tag name from path
        String name = extractTagName(tagPath);
        payload.put("name", name);

        return new CreateResourceAction(
                generateCorrelationId(),
                "tag",
                tagPath,
                payload,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseReadTag(JsonNode args) throws ParseException {
        String tagPath = getRequiredString(args, "tagPath");

        List<String> fields = null;
        if (args.has("includeConfig") && args.get("includeConfig").asBoolean(true)) {
            fields = null; // Include all fields
        }
        if (args.has("includeValue") && !args.get("includeValue").asBoolean(true)) {
            // Exclude value
            fields = List.of("config");
        }

        return new ReadResourceAction(
                generateCorrelationId(),
                "tag",
                tagPath,
                fields,
                false, // includeChildren
                null,  // depth
                parseOptions(args)
        );
    }

    private static UpdateResourceAction parseUpdateTag(JsonNode args) throws ParseException {
        String tagPath = getRequiredString(args, "tagPath");
        Map<String, Object> payload = new LinkedHashMap<>();

        // If explicit changes object provided
        if (args.has("changes")) {
            payload.putAll(nodeToMap(args.get("changes")));
        }

        // Direct field updates
        copyIfPresent(args, payload, "documentation");
        copyIfPresent(args, payload, "engUnit");
        copyIfPresent(args, payload, "expression");
        copyIfPresent(args, payload, "value");

        // Store comment for audit
        if (args.has("comment")) {
            payload.put("_comment", args.get("comment").asText());
        }

        return new UpdateResourceAction(
                generateCorrelationId(),
                "tag",
                tagPath,
                payload,
                true, // merge mode
                parseOptions(args)
        );
    }

    private static DeleteResourceAction parseDeleteTag(JsonNode args) throws ParseException {
        String tagPath = getRequiredString(args, "tagPath");
        boolean recursive = args.has("recursive") && args.get("recursive").asBoolean(false);

        return new DeleteResourceAction(
                generateCorrelationId(),
                "tag",
                tagPath,
                recursive,
                parseOptions(args)
        );
    }

    private static UpdateResourceAction parseWriteTagValue(JsonNode args) throws ParseException {
        String tagPath = getRequiredString(args, "tagPath");
        Map<String, Object> payload = new LinkedHashMap<>();

        if (!args.has("value")) {
            throw new ParseException("write_tag_value requires 'value' parameter");
        }

        payload.put("value", nodeToValue(args.get("value")));
        payload.put("_writeValueOnly", true);

        if (args.has("comment")) {
            payload.put("_comment", args.get("comment").asText());
        }

        return new UpdateResourceAction(
                generateCorrelationId(),
                "tag",
                tagPath,
                payload,
                true,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseListTags(JsonNode args) throws ParseException {
        String parentPath = getRequiredString(args, "parentPath");
        boolean recursive = args.has("recursive") && args.get("recursive").asBoolean(false);

        List<String> fields = new ArrayList<>();
        if (args.has("filter")) {
            fields.add("filter:" + args.get("filter").asText());
        }

        return new ReadResourceAction(
                generateCorrelationId(),
                "tag",
                parentPath + "/*", // Wildcard indicates listing
                fields,
                true, // includeChildren for listing
                recursive ? null : 1, // depth
                parseOptions(args)
        );
    }

    // ==================== View Parsers ====================

    private static CreateResourceAction parseCreateView(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String viewPath = getRequiredString(args, "viewPath");

        Map<String, Object> payload = new LinkedHashMap<>();

        if (args.has("root")) {
            payload.put("root", nodeToMap(args.get("root")));
        } else {
            // Default empty view
            Map<String, Object> defaultRoot = new LinkedHashMap<>();
            defaultRoot.put("type", "ia.container.flex");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("direction", "column");
            defaultRoot.put("props", props);
            payload.put("root", defaultRoot);
        }

        if (args.has("params")) {
            payload.put("params", nodeToMap(args.get("params")));
        }

        String resourcePath = projectName + "/" + viewPath;

        return new CreateResourceAction(
                generateCorrelationId(),
                "perspective-view",
                resourcePath,
                payload,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseReadView(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String viewPath = getRequiredString(args, "viewPath");

        String resourcePath = projectName + "/" + viewPath;

        return new ReadResourceAction(
                generateCorrelationId(),
                "perspective-view",
                resourcePath,
                null, // all fields
                false,
                null,
                parseOptions(args)
        );
    }

    private static UpdateResourceAction parseUpdateView(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String viewPath = getRequiredString(args, "viewPath");

        Map<String, Object> payload = new LinkedHashMap<>();

        if (args.has("changes")) {
            payload.put("changes", nodeToMap(args.get("changes")));
        }

        if (args.has("jsonPatch")) {
            List<Object> patchOps = new ArrayList<>();
            for (JsonNode op : args.get("jsonPatch")) {
                patchOps.add(nodeToMap(op));
            }
            payload.put("jsonPatch", patchOps);
        }

        if (args.has("comment")) {
            payload.put("_comment", args.get("comment").asText());
        }

        String resourcePath = projectName + "/" + viewPath;

        return new UpdateResourceAction(
                generateCorrelationId(),
                "perspective-view",
                resourcePath,
                payload,
                true, // merge
                parseOptions(args)
        );
    }

    private static DeleteResourceAction parseDeleteView(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String viewPath = getRequiredString(args, "viewPath");

        String resourcePath = projectName + "/" + viewPath;

        return new DeleteResourceAction(
                generateCorrelationId(),
                "perspective-view",
                resourcePath,
                false, // not recursive
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseListViews(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String parentPath = args.has("parentPath") ? args.get("parentPath").asText() : "";
        boolean recursive = !args.has("recursive") || args.get("recursive").asBoolean(true);

        String resourcePath = projectName + (parentPath.isEmpty() ? "" : "/" + parentPath) + "/*";

        return new ReadResourceAction(
                generateCorrelationId(),
                "perspective-view",
                resourcePath,
                null,
                true,
                recursive ? null : 1,
                parseOptions(args)
        );
    }

    // ==================== Project Parsers ====================

    private static ReadResourceAction parseListProjects(JsonNode args) {
        List<String> fields = new ArrayList<>();
        if (args.has("includeDisabled") && args.get("includeDisabled").asBoolean(false)) {
            fields.add("includeDisabled");
        }

        return new ReadResourceAction(
                generateCorrelationId(),
                "project",
                "*", // Wildcard for listing
                fields,
                false,
                null,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseReadProject(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");

        return new ReadResourceAction(
                generateCorrelationId(),
                "project",
                projectName,
                null, // all fields
                false,
                null,
                parseOptions(args)
        );
    }

    // ==================== Script Parsers ====================

    private static CreateResourceAction parseCreateScript(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String scriptType = getRequiredString(args, "scriptType");
        String scriptPath = getRequiredString(args, "scriptPath");
        String code = getRequiredString(args, "code");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        copyIfPresent(args, payload, "documentation");

        // Include acknowledgeWarnings flag
        if (args.has("acknowledgeWarnings")) {
            payload.put("acknowledgeWarnings", args.get("acknowledgeWarnings").asBoolean(false));
        }

        String resourcePath = projectName + "/" + scriptType + "/" + scriptPath;

        return new CreateResourceAction(
                generateCorrelationId(),
                "script",
                resourcePath,
                payload,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseReadScript(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String scriptType = getRequiredString(args, "scriptType");
        String scriptPath = getRequiredString(args, "scriptPath");

        String resourcePath = projectName + "/" + scriptType + "/" + scriptPath;

        return new ReadResourceAction(
                generateCorrelationId(),
                "script",
                resourcePath,
                null, // all fields
                false,
                null,
                parseOptions(args)
        );
    }

    private static UpdateResourceAction parseUpdateScript(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String scriptType = getRequiredString(args, "scriptType");
        String scriptPath = getRequiredString(args, "scriptPath");
        String code = getRequiredString(args, "code");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        copyIfPresent(args, payload, "documentation");

        if (args.has("acknowledgeWarnings")) {
            payload.put("acknowledgeWarnings", args.get("acknowledgeWarnings").asBoolean(false));
        }

        if (args.has("comment")) {
            payload.put("_comment", args.get("comment").asText());
        }

        String resourcePath = projectName + "/" + scriptType + "/" + scriptPath;

        return new UpdateResourceAction(
                generateCorrelationId(),
                "script",
                resourcePath,
                payload,
                true, // merge
                parseOptions(args)
        );
    }

    private static DeleteResourceAction parseDeleteScript(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String scriptType = getRequiredString(args, "scriptType");
        String scriptPath = getRequiredString(args, "scriptPath");

        String resourcePath = projectName + "/" + scriptType + "/" + scriptPath;

        return new DeleteResourceAction(
                generateCorrelationId(),
                "script",
                resourcePath,
                false, // not recursive
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseListScripts(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String scriptType = getRequiredString(args, "scriptType");
        String parentPath = args.has("parentPath") ? args.get("parentPath").asText() : "";

        String resourcePath = projectName + "/" + scriptType +
                (parentPath.isEmpty() ? "" : "/" + parentPath) + "/*";

        return new ReadResourceAction(
                generateCorrelationId(),
                "script",
                resourcePath,
                null,
                true, // includeChildren for listing
                null,
                parseOptions(args)
        );
    }

    // ==================== Named Query Parsers ====================

    private static CreateResourceAction parseCreateNamedQuery(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String queryPath = getRequiredString(args, "queryPath");
        String queryType = getRequiredString(args, "queryType");
        String database = getRequiredString(args, "database");
        String query = getRequiredString(args, "query");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queryType", queryType);
        payload.put("database", database);
        payload.put("query", query);

        if (args.has("parameters")) {
            List<Object> params = new ArrayList<>();
            for (JsonNode param : args.get("parameters")) {
                params.add(nodeToMap(param));
            }
            payload.put("parameters", params);
        }

        copyIfPresent(args, payload, "fallbackValue");
        copyIfPresent(args, payload, "cacheEnabled");
        copyIfPresent(args, payload, "cacheExpiry");

        String resourcePath = projectName + "/" + queryPath;

        return new CreateResourceAction(
                generateCorrelationId(),
                "named-query",
                resourcePath,
                payload,
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseReadNamedQuery(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String queryPath = getRequiredString(args, "queryPath");

        String resourcePath = projectName + "/" + queryPath;

        return new ReadResourceAction(
                generateCorrelationId(),
                "named-query",
                resourcePath,
                null, // all fields
                false,
                null,
                parseOptions(args)
        );
    }

    private static UpdateResourceAction parseUpdateNamedQuery(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String queryPath = getRequiredString(args, "queryPath");

        Map<String, Object> payload = new LinkedHashMap<>();

        copyIfPresent(args, payload, "queryType");
        copyIfPresent(args, payload, "database");
        copyIfPresent(args, payload, "query");
        copyIfPresent(args, payload, "fallbackValue");
        copyIfPresent(args, payload, "cacheEnabled");
        copyIfPresent(args, payload, "cacheExpiry");

        if (args.has("parameters")) {
            List<Object> params = new ArrayList<>();
            for (JsonNode param : args.get("parameters")) {
                params.add(nodeToMap(param));
            }
            payload.put("parameters", params);
        }

        if (args.has("comment")) {
            payload.put("_comment", args.get("comment").asText());
        }

        String resourcePath = projectName + "/" + queryPath;

        return new UpdateResourceAction(
                generateCorrelationId(),
                "named-query",
                resourcePath,
                payload,
                true, // merge
                parseOptions(args)
        );
    }

    private static DeleteResourceAction parseDeleteNamedQuery(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String queryPath = getRequiredString(args, "queryPath");

        String resourcePath = projectName + "/" + queryPath;

        return new DeleteResourceAction(
                generateCorrelationId(),
                "named-query",
                resourcePath,
                false, // not recursive
                parseOptions(args)
        );
    }

    private static ReadResourceAction parseListNamedQueries(JsonNode args) throws ParseException {
        String projectName = getRequiredString(args, "projectName");
        String parentPath = args.has("parentPath") ? args.get("parentPath").asText() : "";

        String resourcePath = projectName +
                (parentPath.isEmpty() ? "" : "/" + parentPath) + "/*";

        return new ReadResourceAction(
                generateCorrelationId(),
                "named-query",
                resourcePath,
                null,
                true, // includeChildren for listing
                null,
                parseOptions(args)
        );
    }

    // ==================== Helper Methods ====================

    private static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private static ActionOptions parseOptions(JsonNode args) {
        boolean dryRun = args.has("dryRun") && args.get("dryRun").asBoolean(false);
        boolean force = args.has("force") && args.get("force").asBoolean(false);
        String comment = args.has("comment") ? args.get("comment").asText() : null;

        return new ActionOptions(dryRun, force, comment);
    }

    private static String getRequiredString(JsonNode args, String field) throws ParseException {
        if (!args.has(field) || args.get(field).isNull()) {
            throw new ParseException("Missing required field: " + field);
        }
        return args.get(field).asText();
    }

    private static void copyIfPresent(JsonNode args, Map<String, Object> payload, String field) {
        if (args.has(field) && !args.get(field).isNull()) {
            payload.put(field, nodeToValue(args.get(field)));
        }
    }

    private static Object nodeToValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : node) {
                list.add(nodeToValue(element));
            }
            return list;
        } else if (node.isObject()) {
            return nodeToMap(node);
        }
        return node.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nodeToMap(JsonNode node) {
        try {
            return MAPPER.convertValue(node, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String extractTagName(String tagPath) {
        int lastSlash = tagPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            return tagPath.substring(lastSlash + 1);
        }
        // Handle [provider]TagName format
        int lastBracket = tagPath.lastIndexOf(']');
        if (lastBracket >= 0) {
            return tagPath.substring(lastBracket + 1);
        }
        return tagPath;
    }

    /**
     * Exception thrown when parsing fails.
     */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
