# Security Model

This document describes the security architecture of the Ignition LLM Gateway module.

## Overview

The LLM Gateway module implements defense-in-depth security with multiple layers of protection between external LLM requests and Ignition Gateway resources.

## Security Layers

### 1. Authentication

All requests must include valid Ignition user credentials using HTTP Basic Authentication:

```
Authorization: Basic base64(username:password)
```

**Example:**
```bash
# Authenticate with Ignition admin user
curl -u admin:password http://localhost:8088/system/llm-gateway/info
```

**Security Notes:**
- Credentials are validated against Ignition's configured user sources
- HTTP Basic Auth sends credentials Base64-encoded (NOT encrypted)
- **HTTPS is required in production** to protect credentials in transit
- Ignition's user source handles brute-force protection and account lockout

**User Source Configuration:**
- By default, authenticates against the "default" user source
- Supports any configured Ignition user source (LDAP, Active Directory, database-backed, etc.)

### 2. Authorization (Role-Based Access)

User roles are mapped to module permissions:

| Ignition Role | Module Permissions |
|---------------|-------------------|
| Administrator | Full access (ADMIN) |
| Developer | All CRUD operations on resources |
| Operator | Read/write for tags, read-only for views |
| Viewer/Default | Read-only access |

**Role Mapping Details:**

- **Administrator**: Grants `ADMIN` permission, which implicitly allows all operations
- **Developer**: TAG_*, VIEW_*, SCRIPT_*, NAMED_QUERY_*, PROJECT_READ
- **Operator**: TAG_READ, TAG_WRITE_VALUE, VIEW_READ, PROJECT_READ
- **Viewer**: READ_ALL (all read permissions)

### 3. Policy Engine

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

*Requires explicit policy grant or Administrator role

### 4. Input Validation

All action requests are validated before execution:

- **Schema Validation** - JSON structure must match expected schema
- **Field Validation** - Required fields, valid values, length limits
- **Path Validation** - No path traversal (`..`, `//`)
- **Payload Size Limits** - Maximum 10MB per request

### 5. Destructive Action Safeguards

Destructive operations (delete, replace) require additional safeguards:

1. **Dry-Run First** - Recommended to preview changes
2. **Explicit Confirmation** - In production, `force: true` required
3. **Audit Warning** - Logged with elevated severity
4. **Rollback Information** - Response includes undo instructions when possible

### 6. Audit Logging

All operations are logged to an append-only audit trail:

```json
{
  "id": "audit-entry-uuid",
  "correlationId": "request-correlation-id",
  "timestamp": "2024-01-15T10:30:00Z",
  "category": "LLM_ACTION",
  "eventType": "ACTION_REQUEST",
  "userId": "admin",
  "userSource": "default",
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

### 7. Correlation IDs

Every request must include a unique correlation ID (UUID v4):

- Enables tracing related operations
- Required for audit log queries
- Links request → validation → execution → result

## Secrets Management

**Never expose:**
- Ignition user passwords in logs or responses
- LLM provider API keys
- Database connection strings
- Internal system paths

**Secure Storage:**
- Use Ignition's credential store for secrets
- Environment variables for configuration
- Encrypted storage at rest

## Network Security

**Requirements for Production:**
- **HTTPS is mandatory** - Basic Auth sends credentials in plaintext
- Use valid SSL certificates (not self-signed in production)
- Configure firewall rules to limit access
- Consider API gateway/reverse proxy for additional controls

## Rate Limiting

Rate limiting prevents abuse:

- Per-user request limits
- Per-resource operation limits
- Global throughput limits

## Incident Response

**If a security incident is suspected:**

1. Check audit logs for anomalous activity
2. Disable compromised user accounts immediately
3. Review policy engine decisions
4. Analyze correlation context for related actions
5. Document incident and remediation

## Security Checklist

Before deploying to production:

- [ ] HTTPS enabled with valid certificate (required for Basic Auth)
- [ ] User accounts configured with strong passwords
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
