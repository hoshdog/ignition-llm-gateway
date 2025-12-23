#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod/v3";
import { randomUUID } from "crypto";

// Configuration from environment variables
const GATEWAY_URL = process.env.IGNITION_GATEWAY_URL || "http://localhost:8088";
const API_KEY = process.env.IGNITION_API_KEY || "";

// Helper function to make API requests
async function callIgnitionAPI(
  endpoint: string,
  method: string = "GET",
  body?: object
): Promise<unknown> {
  const url = `${GATEWAY_URL}/system/llm-gateway${endpoint}`;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };

  if (API_KEY) {
    headers["Authorization"] = `Bearer ${API_KEY}`;
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
async function executeAction(
  action: string,
  resourceType: string,
  resourcePath: string,
  payload?: object,
  options?: object
): Promise<unknown> {
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
server.tool(
  "check_health",
  "Check if the Ignition Gateway LLM module is healthy and running",
  {},
  async () => {
    try {
      const result = await callIgnitionAPI("/health");
      return {
        content: [
          {
            type: "text" as const,
            text: JSON.stringify(result, null, 2),
          },
        ],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// List Tag Providers
server.tool(
  "list_tag_providers",
  "List all tag providers in the Ignition Gateway (default, System, Sample_Tags, etc.)",
  {},
  async () => {
    try {
      const result = await executeAction("read", "tag", "*");
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Browse Tags
server.tool(
  "browse_tags",
  "Browse tags in a provider or folder. Use [providerName] format, e.g., [default] or [default]Folder/Path",
  {
    path: z.string().describe("Tag path, e.g., [default] or [default]Folder/SubFolder"),
  },
  async ({ path }) => {
    try {
      const result = await executeAction("read", "tag", path);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Read Tag Value
server.tool(
  "read_tag",
  "Read a specific tag's value and configuration",
  {
    path: z.string().describe("Full tag path, e.g., [default]Folder/TagName"),
  },
  async ({ path }) => {
    try {
      const result = await executeAction("read", "tag", path);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Create Tag
server.tool(
  "create_tag",
  "Create a new tag in the Ignition Gateway. Use tagType: AtomicTag for values, Folder for containers.",
  {
    path: z.string().describe("Tag path, e.g., [default]Folder/NewTag"),
    tagType: z.enum(["AtomicTag", "Folder", "UdtInstance", "OpcTag", "ExpressionTag"]).describe("Type of tag to create"),
    dataType: z.string().optional().describe("Data type for AtomicTag: Int4, Int8, Float4, Float8, Boolean, String, DateTime"),
    value: z.any().optional().describe("Initial value for the tag"),
    dryRun: z.boolean().optional().default(false).describe("If true, validate without creating"),
  },
  async ({ path, tagType, dataType, value, dryRun }) => {
    try {
      const payload: Record<string, unknown> = { tagType };
      if (dataType) payload.dataType = dataType;
      if (value !== undefined) payload.value = value;

      const result = await executeAction("create", "tag", path, payload, { dryRun });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Update Tag Value
server.tool(
  "write_tag_value",
  "Write a value to a tag",
  {
    path: z.string().describe("Full tag path, e.g., [default]Folder/TagName"),
    value: z.any().describe("Value to write to the tag"),
  },
  async ({ path, value }) => {
    try {
      const result = await executeAction("update", "tag", path, { value });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Delete Tag
server.tool(
  "delete_tag",
  "Delete a tag or folder from the Ignition Gateway",
  {
    path: z.string().describe("Tag path to delete, e.g., [default]Folder/TagName"),
    recursive: z.boolean().optional().default(false).describe("Delete folder contents recursively"),
  },
  async ({ path, recursive }) => {
    try {
      const result = await executeAction("delete", "tag", path, undefined, {
        force: true,
        recursive
      });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// List Projects
server.tool(
  "list_projects",
  "List all projects in the Ignition Gateway",
  {},
  async () => {
    try {
      const result = await executeAction("read", "project", "*");
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// List Views
server.tool(
  "list_views",
  "List all Perspective views in a project",
  {
    project: z.string().describe("Project folder name (not display name)"),
  },
  async ({ project }) => {
    try {
      const result = await executeAction("read", "perspective-view", `${project}/*`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Read View
server.tool(
  "read_view",
  "Read a Perspective view's JSON content",
  {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path within the project, e.g., Dashboard or Screens/Main"),
  },
  async ({ project, viewPath }) => {
    try {
      const result = await executeAction("read", "perspective-view", `${project}/${viewPath}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Create View
server.tool(
  "create_view",
  "Create a new Perspective view with a root container",
  {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path, e.g., Dashboard or Screens/NewView"),
    containerType: z.enum(["flex", "coordinate", "breakpoint"]).optional().default("flex").describe("Root container type"),
  },
  async ({ project, viewPath, containerType }) => {
    try {
      const payload = {
        root: {
          type: `ia.container.${containerType}`,
          children: [],
        },
      };
      const result = await executeAction("create", "perspective-view", `${project}/${viewPath}`, payload);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Update View
server.tool(
  "update_view",
  "Update a Perspective view's content",
  {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path"),
    root: z.any().describe("The root component JSON to set for the view"),
  },
  async ({ project, viewPath, root }) => {
    try {
      const result = await executeAction("update", "perspective-view", `${project}/${viewPath}`, { root });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Delete View
server.tool(
  "delete_view",
  "Delete a Perspective view",
  {
    project: z.string().describe("Project folder name"),
    viewPath: z.string().describe("View path to delete"),
  },
  async ({ project, viewPath }) => {
    try {
      const result = await executeAction("delete", "perspective-view", `${project}/${viewPath}`, undefined, { force: true });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// List Scripts
server.tool(
  "list_scripts",
  "List all project library scripts in a project",
  {
    project: z.string().describe("Project folder name"),
  },
  async ({ project }) => {
    try {
      const result = await executeAction("read", "script", `${project}/*`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Read Script
server.tool(
  "read_script",
  "Read a project library script's Python code",
  {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path, e.g., MyPackage/utils"),
  },
  async ({ project, scriptPath }) => {
    try {
      const result = await executeAction("read", "script", `${project}/library/${scriptPath}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Create Script
server.tool(
  "create_script",
  "Create a new project library script with Python code",
  {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path, e.g., MyPackage/utils"),
    code: z.string().describe("Python code content"),
  },
  async ({ project, scriptPath, code }) => {
    try {
      const result = await executeAction("create", "script", `${project}/library/${scriptPath}`, { code });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Update Script
server.tool(
  "update_script",
  "Update an existing project library script",
  {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path"),
    code: z.string().describe("New Python code content"),
  },
  async ({ project, scriptPath, code }) => {
    try {
      const result = await executeAction("update", "script", `${project}/library/${scriptPath}`, { code });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Delete Script
server.tool(
  "delete_script",
  "Delete a project library script",
  {
    project: z.string().describe("Project folder name"),
    scriptPath: z.string().describe("Script path to delete"),
  },
  async ({ project, scriptPath }) => {
    try {
      const result = await executeAction("delete", "script", `${project}/library/${scriptPath}`, undefined, { force: true });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// List Named Queries
server.tool(
  "list_named_queries",
  "List all named queries in a project",
  {
    project: z.string().describe("Project folder name"),
  },
  async ({ project }) => {
    try {
      const result = await executeAction("read", "named-query", `${project}/*`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Read Named Query
server.tool(
  "read_named_query",
  "Read a named query's SQL and configuration",
  {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
  },
  async ({ project, queryName }) => {
    try {
      const result = await executeAction("read", "named-query", `${project}/${queryName}`);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Create Named Query
server.tool(
  "create_named_query",
  "Create a new named query with SQL",
  {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
    query: z.string().describe("SQL query text"),
    queryType: z.enum(["Query", "Update", "Insert", "Delete", "Scalar"]).default("Query").describe("Query type"),
    database: z.string().optional().default("default").describe("Database connection name"),
  },
  async ({ project, queryName, query, queryType, database }) => {
    try {
      const result = await executeAction("create", "named-query", `${project}/${queryName}`, {
        query,
        queryType,
        database,
      });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Update Named Query
server.tool(
  "update_named_query",
  "Update an existing named query",
  {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name"),
    query: z.string().optional().describe("New SQL query text"),
    queryType: z.enum(["Query", "Update", "Insert", "Delete", "Scalar"]).optional().describe("Query type"),
    database: z.string().optional().describe("Database connection name"),
  },
  async ({ project, queryName, query, queryType, database }) => {
    try {
      const payload: Record<string, unknown> = {};
      if (query !== undefined) payload.query = query;
      if (queryType !== undefined) payload.queryType = queryType;
      if (database !== undefined) payload.database = database;

      const result = await executeAction("update", "named-query", `${project}/${queryName}`, payload);
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// Delete Named Query
server.tool(
  "delete_named_query",
  "Delete a named query",
  {
    project: z.string().describe("Project folder name"),
    queryName: z.string().describe("Query name to delete"),
  },
  async ({ project, queryName }) => {
    try {
      const result = await executeAction("delete", "named-query", `${project}/${queryName}`, undefined, { force: true });
      return {
        content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
      };
    } catch (error) {
      return {
        content: [{ type: "text" as const, text: `Error: ${error}` }],
        isError: true,
      };
    }
  }
);

// ============== RESOURCES ==============

// Gateway Health Resource
server.resource(
  "gateway-health",
  "gateway://health",
  {
    description: "Current health status of the Ignition Gateway LLM module",
    mimeType: "application/json",
  },
  async () => {
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
    } catch (error) {
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
  }
);

// ============== START SERVER ==============

async function main() {
  // Log startup info to stderr (Claude Desktop reads stdout for JSON-RPC)
  console.error("=== Ignition MCP Server ===");
  console.error(`Gateway URL: ${GATEWAY_URL}`);
  console.error(`API Key: ${API_KEY ? `configured (${API_KEY.slice(0, 10)}...)` : "NOT SET - API calls may fail"}`);
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
  console.error("  update_named_query, delete_named_query");
}

main().catch((error) => {
  console.error("Fatal error:", error);
  process.exit(1);
});
