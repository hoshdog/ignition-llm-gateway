# Ignition MCP Server

A Model Context Protocol (MCP) server that connects Claude Desktop directly to your Ignition Gateway LLM module.

## Features

- Manage Ignition tags (read, create, update, delete)
- Browse and modify Perspective views
- Create and edit project library scripts
- Manage named queries
- All through natural conversation in Claude Desktop

## Prerequisites

- Node.js 18 or later
- Ignition Gateway with LLM Gateway module installed
- Claude Desktop application
- Valid API key for the LLM Gateway module

## Installation

### 1. Build the Server

```bash
npm install
npm run build
```

### 2. Configure Claude Desktop

Find your Claude Desktop configuration file:

- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

Add the MCP server configuration:

```json
{
  "mcpServers": {
    "ignition-gateway": {
      "command": "node",
      "args": ["C:\\Users\\HoshitoPowell\\Desktop\\Ignition Gateway LLM Module\\ignition-mcp-server\\build\\index.js"],
      "env": {
        "IGNITION_GATEWAY_URL": "http://localhost:8089",
        "IGNITION_API_KEY": "llmgw_YOUR_API_KEY_HERE"
      }
    }
  }
}
```

**Important:** Update the paths and API key for your environment.

### 3. Restart Claude Desktop

Completely quit and restart Claude Desktop for the configuration to take effect.

## Usage

Once configured, you can interact with your Ignition Gateway through Claude Desktop:

**Check Gateway Health:**
```
Check if my Ignition Gateway is healthy
```

**Browse Tags:**
```
List all tag providers in my Ignition Gateway
Show me the tags in the default provider
```

**Create Tags:**
```
Create a new Float8 tag called Temperature in [default]Sensors folder
```

**Manage Views:**
```
List all views in my project
Create a new Perspective view called Dashboard
```

**Work with Scripts:**
```
Show me the scripts in module_test_project
Create a new script with a helper function
```

## Available Tools

| Tool | Description |
|------|-------------|
| `check_health` | Check Gateway LLM module health |
| `list_tag_providers` | List all tag providers |
| `browse_tags` | Browse tags in a path |
| `read_tag` | Read tag value and config |
| `create_tag` | Create a new tag |
| `write_tag_value` | Write value to a tag |
| `delete_tag` | Delete a tag or folder |
| `list_projects` | List all projects |
| `list_views` | List Perspective views |
| `read_view` | Read view content |
| `create_view` | Create new view |
| `update_view` | Update view content |
| `delete_view` | Delete a view |
| `list_scripts` | List project scripts |
| `read_script` | Read script code |
| `create_script` | Create new script |
| `update_script` | Update script code |
| `delete_script` | Delete a script |
| `list_named_queries` | List named queries |
| `read_named_query` | Read query definition |
| `create_named_query` | Create new query |
| `update_named_query` | Update query |
| `delete_named_query` | Delete a query |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `IGNITION_GATEWAY_URL` | Gateway URL | `http://localhost:8088` |
| `IGNITION_API_KEY` | LLM Gateway API key | (required) |

## Troubleshooting

### Server Not Appearing in Claude Desktop

1. Verify the path in config is absolute and correct
2. Check that `build/index.js` exists
3. Look at Claude Desktop logs (Help → Show Logs)

### API Errors

1. Verify Gateway is running: `curl http://localhost:8089/system/llm-gateway/health`
2. Check API key is valid and has required permissions
3. Ensure IGNITION_GATEWAY_URL matches your Gateway

### Creating API Keys

```bash
curl -u admin:password -X POST http://localhost:8089/system/llm-gateway/admin/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-client",
    "permissions": [
      "tag:read", "tag:create", "tag:update", "tag:delete",
      "view:read", "view:create", "view:update", "view:delete",
      "script:read", "script:create", "script:update", "script:delete",
      "named_query:read", "named_query:create", "named_query:update", "named_query:delete",
      "project:read"
    ]
  }'
```

## License

MIT
