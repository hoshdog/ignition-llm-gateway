# CLAUDE.md

> Context file for AI-assisted development of this Ignition Gateway module.

## Project Overview

This repository implements an **Ignition Gateway module** that provides a safe, auditable interface for LLMs (Claude, GPT, Gemini, etc.) to perform CRUD operations on Ignition Gateway resources via natural language.

**Target resources:** Projects, Perspective views, tags, scripts, named queries, alarm pipelines, and other Gateway-managed entities.

**Core principle:** The Gateway is safety-critical infrastructure. All operations default to least-privilege, require authentication/authorization, and produce immutable audit logs.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UX Layer                             │
│         (CLI prompts, error messages, help text)            │
├─────────────────────────────────────────────────────────────┤
│                     Provider Layer                          │
│        (Claude / GPT / Gemini / local model adapters)       │
├─────────────────────────────────────────────────────────────┤
│                      Policy Layer                           │
│   (permissions, confirmations, env mode, constraints)       │
├─────────────────────────────────────────────────────────────┤
│                      Action Layer                           │
│     (strongly-typed CRUD operations on Ignition resources)  │
├─────────────────────────────────────────────────────────────┤
│                      Audit Layer                            │
│      (append-only logs, correlation IDs, trace replay)      │
├─────────────────────────────────────────────────────────────┤
│                   Ignition Gateway APIs                     │
│            (GatewayContext, ProjectManager, etc.)           │
└─────────────────────────────────────────────────────────────┘
```

---

## Build & Run

```bash
# Build the module
mvn clean package

# The .modl file will be in: <module>-build/target/<module>.modl

# Install to Gateway (dev)
# Copy .modl to gateway/user-lib/modules/ and restart, or use Gateway web UI

# Run tests
mvn test

# Run integration tests (requires running Gateway)
mvn verify -Pintegration-tests
```

---

## Module Structure (Ignition conventions)

```
project-root/
├── common/                 # Shared code (gateway + designer + client)
│   └── src/main/java/
├── gateway/                # Gateway-scoped code (runs on Gateway)
│   └── src/main/java/
├── designer/               # Designer-scoped code (optional)
│   └── src/main/java/
├── client/                 # Vision client-scoped code (optional)
│   └── src/main/java/
├── <module>-build/         # Module assembly (produces .modl)
│   └── pom.xml
└── pom.xml                 # Parent POM
```

---

## Key Directories & Files

| Path | Purpose |
|------|---------|
| `gateway/src/.../GatewayHook.java` | Module lifecycle; registers services with GatewayContext |
| `common/src/.../actions/` | Strongly-typed action definitions (CreateTag, UpdateView, etc.) |
| `common/src/.../providers/` | LLM provider adapters |
| `common/src/.../policy/` | Permission checks, environment constraints |
| `common/src/.../audit/` | Audit logging, correlation IDs |
| `.llm/state.json` | Working memory for LLM sessions (if present) |
| `docs/` | Architecture decisions, API docs |

---

## Security Model (Non-Negotiable)

Every CRUD operation **must** pass through:

1. **Authentication** – Verify caller identity (Gateway user, API key, service account)
2. **Authorization** – Check permissions against resource + action (RBAC or policy-based)
3. **Validation** – Schema validation, input sanitization, resource existence checks
4. **Audit logging** – Immutable record: who, what, when, why, correlation ID
5. **Dry-run support** – For destructive/complex operations, preview before apply

### Destructive Operations

Operations that delete or overwrite resources require either:
- Explicit user confirmation (interactive), **or**
- A `force=true` parameter with clear warnings logged

### Secrets

- **Never** output secrets (API keys, passwords, tokens) in logs or responses
- **Never** store secrets in plaintext config files
- Use Ignition's credential store or environment variables with restricted access

---

## Environment Modes

| Mode | Behavior |
|------|----------|
| `dev` | Relaxed confirmations, verbose logging, dry-run defaults to true |
| `test` | Synthetic/mock resources, full audit, no prod data access |
| `prod` | Strict confirmations, minimal logging verbosity, requires explicit force for destructive ops |

Set via Gateway system property or module config: `llm.gateway.mode=dev|test|prod`

---

## Action Request Format

LLMs produce structured action requests (not raw code). The Gateway validates and executes.

```json
{
  "correlationId": "uuid-v4",
  "action": "createTag",
  "params": {
    "path": "[default]MyFolder/NewTag",
    "tagType": "AtomicTag",
    "dataType": "Int4",
    "value": 0
  },
  "dryRun": true,
  "reason": "User requested new counter tag for production line 3"
}
```

**Response:**
```json
{
  "correlationId": "uuid-v4",
  "status": "success|error|pending_confirmation",
  "result": { ... },
  "audit": {
    "timestamp": "ISO-8601",
    "user": "admin",
    "action": "createTag",
    "resourcePath": "[default]MyFolder/NewTag"
  }
}
```

---

## Ignition API Guidelines

- **Prefer public APIs** from `com.inductiveautomation.ignition.*` packages
- **Avoid internal/unsupported APIs** – they break across versions
- Use `GatewayContext` for service access (ProjectManager, TagManager, etc.)
- Treat resources as **versioned, named entities** – avoid hardcoded IDs
- Understand **Perspective vs Vision** scope differences:
  - Perspective resources live in project resources (JSON-based)
  - Vision resources are serialized Java objects

### Useful References

- [Ignition SDK Programmer's Guide](https://www.sdk-docs.inductiveautomation.com/)
- [Ignition SDK Examples](https://github.com/inductiveautomation/ignition-sdk-examples)
- [Javadocs & API Changes](https://github.com/inductiveautomation/ignition-sdk-examples/wiki/Javadocs-&-Notable-API-Changes)
- [Module Development Forum](https://forum.inductiveautomation.com/c/module-development/7)

---

## Testing Strategy

| Test Type | Location | Purpose |
|-----------|----------|---------|
| Unit tests | `*/src/test/java/` | Parsing, validation, permission logic, action serialization |
| Integration tests | `integration-tests/` | Full action execution against test Gateway |
| Mock provider tests | `common/src/test/.../providers/` | LLM adapter response handling |

Run unit tests on every commit. Integration tests require a running Gateway instance.

---

## Code Style

- **Java 11+** (match your Gateway target version)
- Follow existing formatting (prefer IDE auto-format consistency)
- Explicit types over `var` for public APIs
- Null-safety: use `Optional<T>`, `@Nullable`/`@NonNull` annotations
- Prefer immutable data classes for action params and results
- Clear error messages that guide developers to fix issues

---

## Working Memory / State

If an LLM session maintains state, it goes in `.llm/state.json`:

```json
{
  "sessionId": "uuid",
  "currentProject": "MyProject",
  "pendingActions": [],
  "assumptions": [],
  "lastUpdated": "ISO-8601"
}
```

Update this file when state changes. Include in `.gitignore` if ephemeral.

---

## Common Tasks

### Adding a new action type

1. Define action class in `common/src/.../actions/`
2. Add JSON schema in `common/src/main/resources/schemas/`
3. Implement executor in `gateway/src/.../executors/`
4. Register in action registry
5. Add unit tests for validation + serialization
6. Add integration test

### Adding a new LLM provider

1. Implement `LLMProvider` interface in `common/src/.../providers/`
2. Handle auth, rate limits, error mapping
3. Register provider in configuration
4. Add mock tests

### Debugging Gateway-side code

- Logs: Gateway web UI → Status → Logs (filter by module logger)
- Remote debug: Start Gateway with `-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000`
- Use `LoggerFactory.getLogger(YourClass.class)` for structured logging

---

## Do NOT

- ❌ Disable authentication or authorization for "convenience"
- ❌ Store API keys or secrets in code or plain config
- ❌ Execute LLM-generated code directly without validation
- ❌ Skip audit logging for any CRUD operation
- ❌ Use internal Ignition APIs that may break across versions
- ❌ Hardcode resource IDs – use paths/names
- ❌ Auto-approve destructive actions in production mode

---

## Changelog & Decisions

Document significant architecture decisions in `docs/decisions/`:

```
docs/decisions/
├── 001-action-request-format.md
├── 002-audit-log-schema.md
└── 003-provider-abstraction.md
```

Use lightweight ADR format: Context → Decision → Consequences.

---

## Getting Help

- **Ignition SDK:** https://www.sdk-docs.inductiveautomation.com/
- **Forum:** https://forum.inductiveautomation.com/c/module-development/7
- **SDK Examples:** https://github.com/inductiveautomation/ignition-sdk-examples
