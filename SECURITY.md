# Security Model

This document describes the security architecture of the Ignition LLM Gateway module.

## Overview

The LLM Gateway module implements defense-in-depth security with multiple layers of protection between external LLM requests and Ignition Gateway resources.

## Security Layers

### 1. Authentication

All requests must include valid credentials:

```
Authorization: Bearer <oauth-token>
# or
X-API-Key: <api-key>
```

**API Key Management:**
- API keys should be generated with strong randomness (256+ bits)
- Store keys using Ignition's secure credential storage
- Rotate keys periodically
- Never log or output API keys

**OAuth Support:**
- Supports standard Bearer token authentication
- Validate tokens against configured identity provider
- Check token expiration and scopes

### 2. Authorization (Policy Engine)

The policy engine evaluates every request against:

- **Environment Mode** - dev/test/prod with different defaults
- **Action Type** - which CRUD operations are allowed
- **Resource Type** - which resources can be accessed
- **User Permissions** - role-based access control

**Default Policies by Environment:**

| Environment | Read | Create | Update | Delete |
|-------------|------|--------|--------|--------|
| Development | Yes  | Yes    | Yes    | Yes    |
| Test        | Yes  | Yes    | Yes    | Confirm |
| Production  | Yes  | No*    | No*    | No*    |

*Requires explicit policy grant

### 3. Input Validation

All action requests are validated before execution:

- **Schema Validation** - JSON structure must match expected schema
- **Field Validation** - Required fields, valid values, length limits
- **Path Validation** - No path traversal (`..`, `//`)
- **Payload Size Limits** - Maximum 10MB per request

### 4. Destructive Action Safeguards

Destructive operations (delete, replace) require additional safeguards:

1. **Dry-Run First** - Recommended to preview changes
2. **Explicit Confirmation** - In production, `force: true` required
3. **Audit Warning** - Logged with elevated severity
4. **Rollback Information** - Response includes undo instructions when possible

### 5. Audit Logging

All operations are logged to an append-only audit trail:

```json
{
  "id": "audit-entry-uuid",
  "correlationId": "request-correlation-id",
  "timestamp": "2024-01-15T10:30:00Z",
  "category": "LLM_ACTION",
  "eventType": "ACTION_REQUEST",
  "userId": "api-user-123",
  "resourceType": "tag",
  "resourcePath": "TagProvider/Folder/MyTag",
  "actionType": "update",
  "details": { ... }
}
```

**Audit Categories:**
- `LLM_ACTION` - Action requests and results
- `LLM_AUTH` - Authentication and authorization events
- `LLM_POLICY` - Policy decisions
- `LLM_SYSTEM` - System events (startup, shutdown, config)

### 6. Correlation IDs

Every request must include a unique correlation ID (UUID v4):

- Enables tracing related operations
- Required for audit log queries
- Links request → validation → execution → result

## Secrets Management

**Never expose:**
- API keys in logs or responses
- LLM provider credentials
- Database connection strings
- Internal system paths

**Secure Storage:**
- Use Ignition's credential store for secrets
- Environment variables for configuration
- Encrypted storage at rest

## Network Security

**Recommendations:**
- Enable HTTPS for all Gateway traffic
- Use valid SSL certificates (not self-signed in production)
- Configure firewall rules to limit access
- Consider API gateway/reverse proxy for additional controls

## Rate Limiting

Implement rate limiting to prevent abuse:

- Per-user request limits
- Per-resource operation limits
- Global throughput limits

## Incident Response

**If a security incident is suspected:**

1. Check audit logs for anomalous activity
2. Revoke compromised API keys immediately
3. Review policy engine decisions
4. Analyze correlation context for related actions
5. Document incident and remediation

## Security Checklist

Before deploying to production:

- [ ] HTTPS enabled with valid certificate
- [ ] API keys rotated from development values
- [ ] Environment mode set to `production`
- [ ] Audit logging enabled and monitored
- [ ] Rate limiting configured
- [ ] Policy rules reviewed and tested
- [ ] Backup and recovery procedures documented
- [ ] Incident response plan in place

## Reporting Security Issues

If you discover a security vulnerability, please report it responsibly:

1. Do not open a public issue
2. Contact the security team directly
3. Provide detailed reproduction steps
4. Allow reasonable time for a fix before disclosure
