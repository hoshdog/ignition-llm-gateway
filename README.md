# Ignition LLM Gateway Module

A secure interface for Large Language Models (Claude, GPT, Gemini) to perform CRUD operations on Ignition Gateway resources via structured action requests.

## Overview

This module provides a controlled bridge between AI assistants and Ignition, a safety-critical industrial automation system. LLMs produce structured action requests (JSON) that are validated, authorized, and executed by the Gateway—LLMs never directly edit code or configuration.

### Key Features

- **Structured Actions**: Type-safe CRUD operations via JSON schema
- **Policy-Based Access**: Environment-aware permissions (dev/test/prod)
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

### API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/system/llm-gateway/health` | GET | None | Health check |
| `/system/llm-gateway/api/v1/openapi.yaml` | GET | None | OpenAPI specification |
| `/system/llm-gateway/api/v1/chat` | POST | Bearer | Natural language chat |
| `/system/llm-gateway/api/v1/chat/stream` | POST | Bearer | Streaming chat (SSE) |
| `/system/llm-gateway/api/v1/action` | POST | Bearer | Direct action execution |
| `/system/llm-gateway/admin/providers` | GET | Basic | List LLM providers |
| `/system/llm-gateway/admin/providers/{id}/config` | POST | Basic | Configure provider |
| `/system/llm-gateway/admin/api-keys` | GET/POST | Basic | Manage API keys |

### Quick Start

#### 1. Configure LLM Provider

```bash
# Configure Claude (recommended)
curl -u admin:password -X POST \
  http://localhost:8088/system/llm-gateway/admin/providers/claude/config \
  -H "Content-Type: application/json" \
  -d '{"apiKey": "sk-ant-...", "defaultModel": "claude-sonnet-4-20250514", "enabled": true}'
```

#### 2. Create API Key

```bash
# Create an API key with full permissions
curl -X POST http://localhost:8088/system/llm-gateway/admin/api-keys \
  -u admin:password \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-app",
    "permissions": [
      "tag:read", "tag:create", "tag:update", "tag:delete",
      "view:read", "view:create", "view:update", "view:delete",
      "script:read", "script:create", "script:update", "script:delete",
      "named_query:read", "named_query:create", "named_query:update", "named_query:delete",
      "project:read"
    ]
  }'
# Save the rawKey returned - it cannot be retrieved later!
```

#### 3. Chat with the Gateway

```bash
# Use the API key for chat
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/chat \
  -H "Authorization: Bearer llmgw_YOUR_KEY_HERE" \
  -H "Content-Type: application/json" \
  -d '{"message": "List all tag providers and show me what tags exist"}'
```

#### 4. Direct Action Execution

```bash
# Execute structured actions directly (without LLM)
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer llmgw_YOUR_KEY_HERE" \
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

Permissions use the format `{resource_type}:{action}`:

| Permission | Description |
|------------|-------------|
| `tag:read` | Read tag values and browse tag providers |
| `tag:create` | Create new tags and folders |
| `tag:update` | Modify tag values and configuration |
| `tag:delete` | Delete tags and folders |
| `view:read` | Read Perspective views |
| `view:create` | Create new Perspective views |
| `view:update` | Modify Perspective views |
| `view:delete` | Delete Perspective views |
| `script:read` | Read project library scripts |
| `script:create` | Create new scripts |
| `script:update` | Modify scripts |
| `script:delete` | Delete scripts |
| `named_query:read` | Read named queries |
| `named_query:create` | Create new named queries |
| `named_query:update` | Modify named queries |
| `named_query:delete` | Delete named queries |
| `project:read` | List and read project information |

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

- All operations require authentication (API key or OAuth)
- Authorization checked against policy engine
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
│           ├── execution/  # Action execution
│           └── policy/     # Authorization
├── build/                  # Module packaging
│   └── pom.xml            # ignition-maven-plugin config
├── .claude/               # Claude Code working memory
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
