# Ignition LLM Gateway - Production Deployment Guide

## Prerequisites

- Ignition Gateway 8.3.0 or later
- Network access to LLM provider (Claude API, OpenAI API, or local Ollama)
- Gateway administrator credentials

## Installation

### 1. Install the Module

1. Download `LLM-Gateway-signed.modl` (or unsigned for development)
2. Navigate to Gateway → Config → Modules
3. Click "Install or Upgrade a Module"
4. Select the .modl file
5. Accept the certificate (if unsigned, enable unsigned modules first)
6. Restart Gateway if prompted

### 2. Verify Installation

```bash
curl http://YOUR_GATEWAY:8088/system/llm-gateway/health
```

Expected response:
```json
{"module":"LLM Gateway","version":"1.0.0","status":"healthy"}
```

### 3. Configure LLM Provider

#### Option A: Claude (Recommended)

```bash
curl -u admin:password -X POST \
  http://YOUR_GATEWAY:8088/system/llm-gateway/admin/providers/claude/config \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "sk-ant-api03-YOUR_KEY",
    "defaultModel": "claude-sonnet-4-20250514",
    "maxTokens": 4096,
    "enabled": true
  }'
```

#### Option B: OpenAI

```bash
curl -u admin:password -X POST \
  http://YOUR_GATEWAY:8088/system/llm-gateway/admin/providers/openai/config \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "sk-YOUR_KEY",
    "defaultModel": "gpt-4o",
    "maxTokens": 4096,
    "enabled": true
  }'
```

#### Option C: Ollama (Local)

```bash
curl -u admin:password -X POST \
  http://YOUR_GATEWAY:8088/system/llm-gateway/admin/providers/ollama/config \
  -H "Content-Type: application/json" \
  -d '{
    "apiBaseUrl": "http://localhost:11434",
    "defaultModel": "llama3.1:8b",
    "enabled": true
  }'
```

### 4. Create API Keys

Create keys with appropriate permissions for each integration:

```bash
# Read-only key for monitoring dashboards
curl -u admin:password -X POST \
  http://YOUR_GATEWAY:8088/system/llm-gateway/admin/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "monitoring-dashboard",
    "permissions": ["tag:read", "project:read"]
  }'

# Full access key for development
curl -u admin:password -X POST \
  http://YOUR_GATEWAY:8088/system/llm-gateway/admin/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dev-full-access",
    "permissions": [
      "tag:read", "tag:create", "tag:update", "tag:delete",
      "view:read", "view:create", "view:update", "view:delete",
      "script:read", "script:create", "script:update", "script:delete",
      "named_query:read", "named_query:create", "named_query:update", "named_query:delete",
      "project:read"
    ]
  }'
```

**IMPORTANT:** Save the `rawKey` from the response - it's only shown once!

## Security Considerations

### API Key Management

- Store API keys securely (use secrets management)
- Rotate keys periodically
- Use least-privilege permissions
- Create separate keys for each integration

### Network Security

- Use HTTPS in production
- Consider firewall rules for the `/system/llm-gateway` path
- Monitor audit logs for suspicious activity

### LLM Provider Security

- Never commit LLM API keys to source control
- Use environment variables or secrets management
- Monitor LLM API usage and costs

## Environment Modes

Set the environment mode via Gateway system property:

```bash
# Development (default) - most permissive
-Dllm.gateway.environment=development

# Test - moderate restrictions
-Dllm.gateway.environment=test

# Production - strictest, requires confirmations for destructive ops
-Dllm.gateway.environment=production
```

### Mode Behavior

| Mode | Read/List | Create/Update | Delete |
|------|-----------|---------------|--------|
| Development | Allowed | Allowed | Allowed |
| Test | Allowed | Allowed | Requires force=true |
| Production | Allowed | Requires permission | Requires force=true |

## Monitoring

### Health Check

```bash
curl http://YOUR_GATEWAY:8088/system/llm-gateway/health
```

### List Configured Providers

```bash
curl -u admin:password http://YOUR_GATEWAY:8088/system/llm-gateway/admin/providers
```

### List API Keys

```bash
curl -u admin:password http://YOUR_GATEWAY:8088/system/llm-gateway/admin/api-keys
```

## Troubleshooting

### Module Not Loading

1. Check Gateway logs: `logs/wrapper.log`
2. Verify Ignition version is 8.3.0+
3. Ensure unsigned modules are allowed (if applicable)
4. Check for conflicting module dependencies

### Chat Returns 500 Error

1. Verify LLM provider is configured and enabled
2. Check provider API key is valid
3. Test provider connectivity (network, firewall)
4. Review Gateway logs for stack traces

### Permission Denied (403)

1. Verify API key has required permissions
2. Check permission format (e.g., `named_query:read` not `named-query:read`)
3. Verify API key is active (not deleted)
4. Check environment mode restrictions

### Resources Not Appearing in Designer

After creating resources via the module, you may need to:
1. Refresh the project in Designer (right-click project → Scan Project)
2. Or restart the Gateway for filesystem changes to sync

### Connection Timeout to LLM Provider

1. Check network connectivity to provider API
2. Verify firewall allows outbound HTTPS
3. For Ollama, ensure the service is running
4. Increase timeout settings if needed

## Backup and Recovery

### Export Configuration

The module stores configuration in the Gateway's internal database:
- API keys (hashed)
- Provider configurations
- Audit logs

Back up the Gateway database regularly using Ignition's built-in backup tools.

### Configuration Persistence

Note: API keys and provider configurations are stored in memory and may be cleared on Gateway restart. For production, consider:
- Automating configuration via startup scripts
- Using configuration management tools
- Storing configuration in version control (without secrets)

### Audit Logs

All operations are logged with correlation IDs. Review logs at:
- Gateway logs: `logs/wrapper.log`
- Filter by logger: `com.inductiveautomation.ignition.gateway.llm`
- Search for correlation IDs to trace operations

## Performance Tuning

### LLM Provider Selection

| Provider | Latency | Accuracy | Cost |
|----------|---------|----------|------|
| Claude Sonnet | 2-5s | Excellent | $ |
| GPT-4o | 2-5s | Excellent | $ |
| Ollama (llama3.1:8b) | 15-60s | Good | Free |
| Ollama (llama3.2:3b) | 5-20s | Fair | Free |

### Recommendations

- **Production**: Use Claude or GPT-4o for best accuracy and response times
- **Development/Testing**: Ollama is suitable for local development
- **Cost Optimization**: Consider caching repeated queries
- **High Availability**: Configure multiple providers as fallback

### Resource Limits

- Default request timeout: 120 seconds
- Maximum concurrent requests: 10 (configurable)
- Rate limiting: Infrastructure present, not enforced (future)

## API Reference

### Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/system/llm-gateway/health` | GET | None | Health check |
| `/system/llm-gateway/api/v1/chat` | POST | Bearer | Natural language chat |
| `/system/llm-gateway/api/v1/chat/stream` | POST | Bearer | Streaming chat (SSE) |
| `/system/llm-gateway/api/v1/action` | POST | Bearer | Direct action execution |
| `/system/llm-gateway/admin/providers` | GET | Basic | List LLM providers |
| `/system/llm-gateway/admin/providers/{id}/config` | POST | Basic | Configure provider |
| `/system/llm-gateway/admin/api-keys` | GET/POST/DELETE | Basic | Manage API keys |

### Resource Types

| Resource Type | Permissions | Handler |
|---------------|-------------|---------|
| `tag` | tag:read/create/update/delete | TagManager API |
| `perspective-view` | view:read/create/update/delete | Filesystem |
| `script` | script:read/create/update/delete | Filesystem |
| `named-query` | named_query:read/create/update/delete | Filesystem |
| `project` | project:read | Filesystem |

## Support

For issues and feature requests:
1. Check Gateway logs for error details
2. Review this documentation
3. Submit issues with correlation IDs for faster debugging
