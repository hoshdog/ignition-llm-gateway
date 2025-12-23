#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod/v3";
import { randomUUID } from "crypto";
// Configuration from environment variables
const GATEWAY_URL = process.env.IGNITION_GATEWAY_URL || "http://localhost:8088";
const IGNITION_USERNAME = process.env.IGNITION_USERNAME || "";
const IGNITION_PASSWORD = process.env.IGNITION_PASSWORD || "";
// Helper function to make API requests with Basic Auth
async function callIgnitionAPI(endpoint, method = "GET", body) {
    const url = `${GATEWAY_URL}/system/llm-gateway${endpoint}`;
    const headers = {
        "Content-Type": "application/json",
    };
    // Use HTTP Basic Authentication with Ignition credentials
    if (IGNITION_USERNAME && IGNITION_PASSWORD) {
        const credentials = Buffer.from(`${IGNITION_USERNAME}:${IGNITION_PASSWORD}`).toString("base64");
        headers["Authorization"] = `Basic ${credentials}`;
    }
    const response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(`API Error (${response.status}): ${error}`);
    }
    return response.json();
}
// Helper to execute actions
async function executeAction(action, resourceType, resourcePath, payload, options) {
    return callIgnitionAPI("/api/v1/action", "POST", {
        correlationId: randomUUID(),
        action,
        resourceType,
        resourcePath,
        payload,
        options,
    });
}
// Create MCP Server
const server = new McpServer({
    name: "ignition-gateway",
    version: "1.0.0",
});
// ============== TOOLS ==============
// Health Check
server.tool("check_health", "Check if the Ignition Gateway LLM module is healthy and running", {}, async () => {
    try {
        const result = await callIgnitionAPI("/health");
        return {
            content: [
                {
                    type: "text",
                    text: JSON.stringify(result, null, 2),
                },
            ],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// List Tag Providers
server.tool("list_tag_providers", "List all tag providers in the Ignition Gateway (default, System, Sample_Tags, etc.)", {}, async () => {
    try {
        const result = await executeAction("read", "tag", "*");
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Browse Tags
server.tool("browse_tags", "Browse tags in a provider or folder. Use [providerName] format, e.g., [default] or [default]Folder/Path", {
    path: z.string().describe("Tag path, e.g., [default] or [default]Folder/SubFolder"),
}, async ({ path }) => {
    try {
        const result = await executeAction("read", "tag", path);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Read Tag Value
server.tool("read_tag", "Read a specific tag's value and configuration", {
    path: z.string().describe("Full tag path, e.g., [default]Folder/TagName"),
}, async ({ path }) => {
    try {
        const result = await executeAction("read", "tag", path);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Create Tag
server.tool("create_tag", "Create a new tag in the Ignition Gateway. Use tagType: AtomicTag for values, Folder for containers.", {
    path: z.string().describe("Tag path, e.g., [default]Folder/NewTag"),
    tagType: z.enum(["AtomicTag", "Folder", "UdtInstance", "OpcTag", "ExpressionTag"]).describe("Type of tag to create"),
    dataType: z.string().optional().describe("Data type for AtomicTag: Int4, Int8, Float4, Float8, Boolean, String, DateTime"),
    value: z.any().optional().describe("Initial value for the tag"),
    dryRun: z.boolean().optional().default(false).describe("If true, validate without creating"),
}, async ({ path, tagType, dataType, value, dryRun }) => {
    try {
        const payload = { tagType };
        if (dataType)
            payload.dataType = dataType;
        if (value !== undefined)
            payload.value = value;
        const result = await executeAction("create", "tag", path, payload, { dryRun });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Update Tag Value
server.tool("write_tag_value", "Write a value to a tag", {
    path: z.string().describe("Full tag path, e.g., [default]Folder/TagName"),
    value: z.any().describe("Value to write to the tag"),
}, async ({ path, value }) => {
    try {
        const result = await executeAction("update", "tag", path, { value });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Delete Tag
server.tool("delete_tag", "Delete a tag or folder from the Ignition Gateway", {
    path: z.string().describe("Tag path to delete, e.g., [default]Folder/TagName"),
    recursive: z.boolean().optional().default(false).describe("Delete folder contents recursively"),
}, async ({ path, recursive }) => {
    try {
        const result = await executeAction("delete", "tag", path, undefined, {
            force: true,
            recursive
        });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// List Projects
server.tool("list_projects", "List all projects in the Ignition Gateway", {}, async () => {
    try {
        const result = await executeAction("read", "project", "*");
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// List Views
server.tool("list_views", "List all Perspective views in a project", {
    project: z.string().describe("Project folder name (not display name)"),
}, async ({ project }) => {
    try {
        const result = await executeAction("read", "perspective-view", `${project}/*`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Read View
server.tool("read_view", "Read a Perspective view's JSON content", {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path within the project, e.g., Dashboard or Screens/Main"),
}, async ({ project, viewPath }) => {
    try {
        const result = await executeAction("read", "perspective-view", `${project}/${viewPath}`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Create View
server.tool("create_view", `Create a new Perspective view with a root container.

CONTAINER TYPES:
- flex: Flexbox layout (most common, recommended for responsive layouts)
- coord: Absolute positioning (fixed x/y coordinates)
- breakpoint: Responsive breakpoints for different screen sizes

After creating a view, use update_view to add components.

COMPONENT TYPES REFERENCE:
- Containers: ia.container.flex, ia.container.coord, ia.container.tab, ia.container.split
- Display: ia.display.label, ia.display.icon, ia.display.image, ia.display.markdown, ia.display.gauge
- Input: ia.input.button, ia.input.text-field, ia.input.dropdown, ia.input.checkbox, ia.input.slider
- Charts: ia.chart.pie, ia.chart.bar, ia.chart.time-series-chart, ia.chart.power-chart
- Tables: ia.table.table, ia.table.power-table
- Navigation: ia.navigation.tree, ia.navigation.menu-tree

IMPORTANT: After creation, click File > Update Project in Designer to see the view.`, {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path, e.g., Dashboard or Screens/NewView"),
    containerType: z.enum(["flex", "coord", "breakpoint"]).optional().default("flex").describe("Root container type"),
}, async ({ project, viewPath, containerType }) => {
    try {
        const payload = {
            root: {
                type: `ia.container.${containerType}`,
                children: [],
            },
        };
        const result = await executeAction("create", "perspective-view", `${project}/${viewPath}`, payload);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Update View
server.tool("update_view", `Update a Perspective view's content with a new root component structure.

COMPONENT STRUCTURE EXAMPLE:
{
  "type": "ia.container.flex",
  "version": 0,
  "meta": {"name": "root"},
  "props": {"direction": "column"},
  "children": [
    {
      "type": "ia.display.label",
      "version": 0,
      "meta": {"name": "Title"},
      "position": {"grow": 0, "shrink": 0, "basis": "auto"},
      "props": {"text": "My Title", "style": {"fontSize": "24px"}}
    },
    {
      "type": "ia.input.button",
      "version": 0,
      "meta": {"name": "SubmitBtn"},
      "position": {"grow": 0, "shrink": 0, "basis": "auto"},
      "props": {"text": "Submit"}
    }
  ]
}

REQUIRED for each component:
- type: Component type (e.g., ia.display.label)
- version: Always 0
- meta.name: Unique name within parent
- position: Layout info (for flex: grow/shrink/basis; for coord: x/y/width/height)
- props: Component-specific properties

POSITION for flex container children:
  {"grow": 0, "shrink": 0, "basis": "auto"}

POSITION for coord container children:
  {"x": 100, "y": 50, "width": 200, "height": 30}

IMPORTANT: After update, click File > Update Project in Designer to see changes.`, {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path"),
    root: z.any().describe("The root component JSON to set for the view"),
}, async ({ project, viewPath, root }) => {
    try {
        const result = await executeAction("update", "perspective-view", `${project}/${viewPath}`, { root });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Delete View
server.tool("delete_view", "Delete a Perspective view", {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path to delete"),
}, async ({ project, viewPath }) => {
    try {
        const result = await executeAction("delete", "perspective-view", `${project}/${viewPath}`, undefined, { force: true });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// List Scripts
server.tool("list_scripts", "List all project library scripts in a project", {
    project: z.string().describe("Project folder name"),
}, async ({ project }) => {
    try {
        const result = await executeAction("read", "script", `${project}/*`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Read Script
server.tool("read_script", "Read a project library script's Python code", {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path, e.g., MyPackage/utils"),
}, async ({ project, scriptPath }) => {
    try {
        const result = await executeAction("read", "script", `${project}/library/${scriptPath}`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Create Script
server.tool("create_script", "Create a new project library script with Python code", {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path, e.g., MyPackage/utils"),
    code: z.string().describe("Python code content"),
}, async ({ project, scriptPath, code }) => {
    try {
        const result = await executeAction("create", "script", `${project}/library/${scriptPath}`, { code });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Update Script
server.tool("update_script", "Update an existing project library script", {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path"),
    code: z.string().describe("New Python code content"),
}, async ({ project, scriptPath, code }) => {
    try {
        const result = await executeAction("update", "script", `${project}/library/${scriptPath}`, { code });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Delete Script
server.tool("delete_script", "Delete a project library script", {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path to delete"),
}, async ({ project, scriptPath }) => {
    try {
        const result = await executeAction("delete", "script", `${project}/library/${scriptPath}`, undefined, { force: true });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// List Named Queries
server.tool("list_named_queries", "List all named queries in a project", {
    project: z.string().describe("Project folder name"),
}, async ({ project }) => {
    try {
        const result = await executeAction("read", "named-query", `${project}/*`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Read Named Query
server.tool("read_named_query", "Read a named query's SQL and configuration", {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
}, async ({ project, queryName }) => {
    try {
        const result = await executeAction("read", "named-query", `${project}/${queryName}`);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Create Named Query
server.tool("create_named_query", "Create a new named query with SQL", {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
    query: z.string().describe("SQL query text"),
    queryType: z.enum(["Query", "Update", "Insert", "Delete", "Scalar"]).default("Query").describe("Query type"),
    database: z.string().optional().default("default").describe("Database connection name"),
}, async ({ project, queryName, query, queryType, database }) => {
    try {
        const result = await executeAction("create", "named-query", `${project}/${queryName}`, {
            query,
            queryType,
            database,
        });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Update Named Query
server.tool("update_named_query", "Update an existing named query", {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
    query: z.string().optional().describe("New SQL query text"),
    queryType: z.enum(["Query", "Update", "Insert", "Delete", "Scalar"]).optional().describe("Query type"),
    database: z.string().optional().describe("Database connection name"),
}, async ({ project, queryName, query, queryType, database }) => {
    try {
        const payload = {};
        if (query !== undefined)
            payload.query = query;
        if (queryType !== undefined)
            payload.queryType = queryType;
        if (database !== undefined)
            payload.database = database;
        const result = await executeAction("update", "named-query", `${project}/${queryName}`, payload);
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Delete Named Query
server.tool("delete_named_query", "Delete a named query", {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name to delete"),
}, async ({ project, queryName }) => {
    try {
        const result = await executeAction("delete", "named-query", `${project}/${queryName}`, undefined, { force: true });
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// Trigger Project Scan
server.tool("trigger_project_scan", `Trigger a Gateway project resource scan to detect filesystem changes.

Call this after creating or modifying resources via the API to ensure the Gateway
detects the changes. After the scan completes, use File > Update Project in
Designer to see the changes.`, {}, async () => {
    try {
        const url = `${GATEWAY_URL}/system/llm-gateway-scan/`;
        const headers = {
            "Content-Type": "application/json",
        };
        // Use HTTP Basic Authentication with Ignition credentials
        if (IGNITION_USERNAME && IGNITION_PASSWORD) {
            const credentials = Buffer.from(`${IGNITION_USERNAME}:${IGNITION_PASSWORD}`).toString("base64");
            headers["Authorization"] = `Basic ${credentials}`;
        }
        const response = await fetch(url, {
            method: "POST",
            headers,
        });
        if (!response.ok) {
            const error = await response.text();
            throw new Error(`API Error (${response.status}): ${error}`);
        }
        const result = await response.json();
        return {
            content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
        };
    }
    catch (error) {
        return {
            content: [{ type: "text", text: `Error: ${error}` }],
            isError: true,
        };
    }
});
// ============== RESOURCES ==============
// Gateway Health Resource
server.resource("gateway-health", "gateway://health", {
    description: "Current health status of the Ignition Gateway LLM module",
    mimeType: "application/json",
}, async () => {
    try {
        const health = await callIgnitionAPI("/health");
        return {
            contents: [
                {
                    uri: "gateway://health",
                    mimeType: "application/json",
                    text: JSON.stringify(health, null, 2),
                },
            ],
        };
    }
    catch (error) {
        return {
            contents: [
                {
                    uri: "gateway://health",
                    mimeType: "text/plain",
                    text: `Error: ${error}`,
                },
            ],
        };
    }
});
// ============== START SERVER ==============
async function main() {
    // Log startup info to stderr (Claude Desktop reads stdout for JSON-RPC)
    console.error("=== Ignition MCP Server ===");
    console.error(`Gateway URL: ${GATEWAY_URL}`);
    console.error(`Auth: ${IGNITION_USERNAME ? `configured for user '${IGNITION_USERNAME}'` : "NOT SET - API calls may fail"}`);
    console.error("");
    // Create stdio transport
    const transport = new StdioServerTransport();
    // Connect server to transport
    await server.connect(transport);
    console.error("Ignition MCP Server connected and ready.");
    console.error("Available tools: check_health, list_tag_providers, browse_tags, read_tag,");
    console.error("  create_tag, write_tag_value, delete_tag, list_projects, list_views,");
    console.error("  read_view, create_view, update_view, delete_view, list_scripts,");
    console.error("  read_script, create_script, update_script, delete_script,");
    console.error("  list_named_queries, read_named_query, create_named_query,");
    console.error("  update_named_query, delete_named_query, trigger_project_scan");
}
main().catch((error) => {
    console.error("Fatal error:", error);
    process.exit(1);
});
