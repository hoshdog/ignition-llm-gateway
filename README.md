# Ignition LLM Gateway Module

A secure interface for Large Language Models (Claude, GPT, Gemini) to perform CRUD operations on Ignition Gateway resources via structured action requests.

## Overview

This module provides a controlled bridge between AI assistants and Ignition, a safety-critical industrial automation system. LLMs produce structured action requests (JSON) that are validated, authorized, and executed by the Gateway—LLMs never directly edit code or configuration.

### Key Features

- **Structured Actions**: Type-safe CRUD operations via JSON schema
- **Native Ignition Authentication**: Uses existing Ignition user accounts (Basic Auth)
- **Role-Based Access**: Ignition roles map to module permissions
- **Full Auditability**: Append-only logs with correlation IDs
- **Dry-Run Support**: Preview changes before execution
- **Destructive Action Safeguards**: Confirmation required for deletes in production

### Target Resources

- Projects (Perspective, Vision)
- Perspective Views
- Tags (configuration and values)
- Scripts (project scripts, tag event scripts)
- Named Queries
- Gateway configuration resources

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  LLM Providers (Claude, GPT, Gemini adapters)       │
├─────────────────────────────────────────────────────┤
│  Policy Layer (auth, permissions, environment mode) │
├─────────────────────────────────────────────────────┤
│  Action Layer (typed CRUD operations)               │
├─────────────────────────────────────────────────────┤
│  Audit Layer (append-only logs, correlation IDs)    │
├─────────────────────────────────────────────────────┤
│  Ignition Gateway APIs                              │
└─────────────────────────────────────────────────────┘
```

## Prerequisites

- Ignition Gateway 8.3.0 or later (required for filesystem-based resource access)
- Java 17 or later
- Maven 3.8 or later

## Building

```bash
# Clone the repository
git clone https://github.com/your-org/ignition-llm-gateway.git
cd ignition-llm-gateway

# Build the module
mvn clean package

# The .modl file will be at:
# build/target/llm-gateway-1.0.0-SNAPSHOT.modl
```

## Installation

1. Build the module using Maven
2. Open Ignition Gateway web interface
3. Navigate to **Config > Modules**
4. Click **Install or Upgrade a Module**
5. Upload the `.modl` file from `build/target/`
6. Accept the license and restart if prompted

## Configuration

### Environment Mode

Set the environment mode via system property:

```bash
# Development (default) - most permissive
-Dllm.gateway.environment=development

# Test - moderate restrictions
-Dllm.gateway.environment=test

# Production - strictest, requires confirmations
-Dllm.gateway.environment=production
```

### Authentication

This module uses **HTTP Basic Authentication** with Ignition user credentials:

```bash
# All authenticated endpoints use -u username:password
curl -u admin:password http://localhost:8088/system/llm-gateway/info
```

**Security Note:** Basic Auth sends credentials Base64-encoded (not encrypted). Use HTTPS in production.

### Role-to-Permission Mapping

User roles from Ignition's user sources are mapped to module permissions:

| Ignition Role | Permissions |
|---------------|-------------|
| Administrator | Full access (all operations) |
| Developer | Full CRUD on all resource types |
| Operator | Read/write tags, read-only views |
| Viewer/Default | Read-only access |

### API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/system/llm-gateway/health` | GET | None | Health check |
| `/system/llm-gateway/info` | GET | Basic | Module info & user permissions |
| `/system/llm-gateway/openapi.yaml` | GET | None | OpenAPI specification |
| `/system/llm-gateway/chat` | POST | Basic | Natural language chat |
| `/system/llm-gateway/chat/stream` | POST | Basic | Streaming chat (SSE) |
| `/system/llm-gateway/action` | POST | Basic | Direct action execution |
| `/system/llm-gateway/admin/providers` | GET | Basic (Admin) | List LLM providers |
| `/system/llm-gateway/admin/providers/{id}/config` | POST | Basic (Admin) | Configure provider |
| `/system/llm-gateway/admin/roles` | GET | Basic (Admin) | View role mappings |

### Quick Start

#### 1. Check Module Health

```bash
# No authentication required
curl http://localhost:8088/system/llm-gateway/health
```

#### 2. View Your Permissions

```bash
# Authenticate with your Ignition credentials
curl -u admin:password http://localhost:8088/system/llm-gateway/info
```

#### 3. Configure LLM Provider

```bash
# Configure Claude (requires admin role)
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/admin/providers/claude/config \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "sk-ant-...", "defaultModel": "claude-sonnet-4-20250514", "enabled": true}'
```

#### 4. Chat with the Gateway

```bash
# Use your Ignition credentials for chat
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "List all tag providers and show me what tags exist"}'
```

#### 5. Direct Action Execution

```bash
# Execute structured actions directly (without LLM)
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/action \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "action": "create",
    "resourceType": "tag",
    "resourcePath": "[default]MyFolder/Temperature",
    "payload": {"tagType": "AtomicTag", "dataType": "Float8", "value": 72.5}
  }'
```

### Tag Path Format

Tags use the format `[provider]path/to/tag`:
- `[default]` - Root of default provider
- `[default]Folder/Tag` - Tag in default provider
- `[Sample_Tags]Realistic/Realistic0` - Tag in Sample_Tags provider
- Use `*` with list_tags to list ALL providers

### LLM Provider Recommendations

| Provider | Tool Calling | Best For |
|----------|--------------|----------|
| Claude (claude-sonnet-4-20250514) | Excellent | Production |
| GPT-4o | Excellent | Production |
| llama3.1:8b (Ollama) | Good | Development |
| llama3.2:3b (Ollama) | Fair | Quick testing |

## Action Request Schema

```json
{
  "correlationId": "uuid-v4",
  "action": "update",
  "resourceType": "perspective-view",
  "resourcePath": "MyProject/Views/MainScreen",
  "payload": {
    "root": { ... }
  },
  "options": {
    "dryRun": true,
    "force": false,
    "comment": "Updated button styling per user request"
  }
}
```

### Action Types

- `create` - Create a new resource
- `read` - Read/retrieve a resource
- `update` - Update an existing resource
- `delete` - Delete a resource
- `list` - List resources matching criteria

### Resource Types

| Resource Type | Actions | Notes |
|---------------|---------|-------|
| `tag` | read, create, update, delete | Full CRUD via TagManager API |
| `project` | read | List projects |
| `perspective-view` | read, create, update, delete | Filesystem-based (Ignition 8.3+) |
| `script` | read, create, update, delete | Filesystem-based (Ignition 8.3+) |
| `named-query` | read, create, update, delete | Filesystem-based (Ignition 8.3+) |

### Permissions Reference

Permissions are mapped from Ignition roles. Key permissions include:

| Permission | Description |
|------------|-------------|
| `ADMIN` | Full access to all operations |
| `TAG_READ` | Read tag values and browse tag providers |
| `TAG_CREATE` | Create new tags and folders |
| `TAG_UPDATE` | Modify tag configuration |
| `TAG_WRITE_VALUE` | Write values to existing tags |
| `TAG_DELETE` | Delete tags and folders |
| `VIEW_READ` | Read Perspective views |
| `VIEW_CREATE` | Create new Perspective views |
| `VIEW_UPDATE` | Modify Perspective views |
| `VIEW_DELETE` | Delete Perspective views |
| `SCRIPT_READ` | Read project library scripts |
| `SCRIPT_CREATE` | Create new scripts |
| `SCRIPT_UPDATE` | Modify scripts |
| `SCRIPT_DELETE` | Delete scripts |
| `NAMED_QUERY_READ` | Read named queries |
| `NAMED_QUERY_CREATE` | Create new named queries |
| `NAMED_QUERY_UPDATE` | Modify named queries |
| `NAMED_QUERY_DELETE` | Delete named queries |
| `PROJECT_READ` | List and read project information |

### Path Formats

| Resource | Path Format | Example |
|----------|-------------|---------|
| Tags | `[provider]path/to/tag` | `[default]Folder/Temperature` |
| Views | `{project}/{viewPath}` | `module_test_project/Screens/Dashboard` |
| Scripts | `{project}/library/{path}` | `module_test_project/library/Utils/helpers` |
| Named Queries | `{project}/{queryName}` | `module_test_project/GetActiveAlarms` |

**Note:** For Views, Scripts, and Named Queries, use the project **folder name** (e.g., `module_test_project`), not the display name (e.g., `Main Project`).

## Security

See [SECURITY.md](SECURITY.md) for the complete security model.

### Key Points

- Uses Ignition's built-in user authentication (Basic Auth)
- User roles determine module permissions
- HTTPS required in production (Basic Auth sends credentials in plaintext)
- Full audit trail with correlation IDs
- Destructive actions require confirmation in production
- Dry-run mode for previewing changes

## Project Structure

```
ignition-llm-gateway/
├── common/                 # Shared models and utilities
│   └── src/main/java/
│       └── com/inductiveautomation/ignition/gateway/llm/
│           ├── common/     # Constants and models
│           └── actions/    # Action type definitions
├── gateway/                # Gateway-scoped implementation
│   └── src/main/java/
│       └── com/inductiveautomation/ignition/gateway/llm/gateway/
│           ├── api/        # REST endpoints
│           ├── audit/      # Audit logging
│           ├── auth/       # Authentication (Basic Auth)
│           ├── execution/  # Action execution
│           └── policy/     # Authorization
├── build/                  # Module packaging
│   └── pom.xml            # ignition-maven-plugin config
├── ignition-mcp-server/   # MCP server for Claude Desktop
├── pom.xml               # Parent POM
└── README.md
```

## Development

### Running Tests

```bash
mvn test
```

### Code Style

- Use explicit types (no `var` for complex types)
- Clear error messages that guide developers
- Comprehensive logging at DEBUG level
- Unit tests for parsing, validation, policy logic

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

[Your License Here]

## Support

For issues and feature requests, please use the GitHub issue tracker.
