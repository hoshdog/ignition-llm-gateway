# LLM Gateway Resource Handler Status Report

**Date:** 2025-12-19
**Module Version:** 1.0.0-SNAPSHOT
**Build Status:** SUCCESS

---

## Executive Summary

All resource handlers now implement **real CRUD operations** using direct filesystem access to Ignition project resources (Ignition 8.3+). This approach provides full Create, Read, Update, and Delete functionality for all resource types.

---

## Handler Status

| Handler | Status | CRUD Support | Implementation Approach |
|---------|--------|--------------|------------------------|
| **TagResourceHandler** | OPERATIONAL | Full CRUD | Ignition TagManager API |
| **ViewResourceHandler** | OPERATIONAL | Full CRUD | Filesystem (Ignition 8.3+) |
| **ScriptResourceHandler** | OPERATIONAL | Full CRUD | Filesystem (Ignition 8.3+) |
| **NamedQueryResourceHandler** | OPERATIONAL | Full CRUD | Filesystem (Ignition 8.3+) |

---

## Implementation Details

### TagResourceHandler (TagManager API)

Uses the public `TagManager` API from the Ignition SDK:

```java
gatewayContext.getTagManager().getTagProvider(providerName)
provider.browseAsync(...)
provider.readAsync(...)
provider.writeAsync(...)
```

**Capabilities:**
- LIST: Enumerate tags in any provider
- READ: Get tag configuration and current value
- CREATE: Create new tags with full configuration
- UPDATE: Modify tag properties and values
- DELETE: Remove tags (with recursive option for folders)

### ViewResourceHandler (Filesystem Access)

Uses direct filesystem access to Perspective view resources:

**File Structure:**
```
{IGNITION_DATA}/projects/{PROJECT}/
└── com.inductiveautomation.perspective/
    └── views/
        └── {ViewPath}/
            ├── view.json      # View configuration
            └── resource.json  # Resource metadata
```

**Capabilities:**
- LIST: Walk views directory to find view.json files
- READ: Parse view.json content
- CREATE: Create view directory with view.json and resource.json
- UPDATE: Modify view.json content
- DELETE: Remove view directory and all files

### ScriptResourceHandler (Filesystem Access)

Uses direct filesystem access to Python script resources:

**File Structure:**
```
{IGNITION_DATA}/projects/{PROJECT}/
└── ignition/
    └── script-python/
        └── {ScriptPath}/
            ├── code.py        # Python script code
            └── resource.json  # Resource metadata
```

**Capabilities:**
- LIST: Walk script-python directory to find code.py files
- READ: Read code.py content
- CREATE: Create script directory with code.py and resource.json
- UPDATE: Modify code.py content
- DELETE: Remove script directory and all files
- **Security**: Blocked patterns for dangerous operations (os.system, eval, etc.)

### NamedQueryResourceHandler (Filesystem Access)

Uses direct filesystem access to Named Query resources:

**File Structure:**
```
{IGNITION_DATA}/projects/{PROJECT}/
└── ignition/
    └── named-query/
        └── {QueryPath}/
            ├── query.sql      # SQL query
            ├── query.json     # Query configuration (parameters, database, etc.)
            └── resource.json  # Resource metadata
```

**Capabilities:**
- LIST: Walk named-query directory to find query.sql files
- READ: Read query.sql and query.json
- CREATE: Create query directory with all required files
- UPDATE: Modify query.sql and/or query.json
- DELETE: Remove query directory and all files
- **Security**: SQL injection prevention checks

---

## SDK Research Findings

### Classes NOT Available in Public SDK

The following classes from `com.inductiveautomation.ignition.common.project` are NOT exposed in the public SDK JARs:

| Class | Purpose | Available? |
|-------|---------|------------|
| `RuntimeProject` | Access loaded project resources | Internal |
| `ProjectResource` | Read/write project resource content | Internal |
| `ResourcePath` | Construct paths to project resources | Internal |
| `ResourceType` | Identify resource types | Internal |
| `ProjectManager.getProject()` | Get project instance | Internal |

### Classes Available in Public SDK

| Class | Purpose | Status |
|-------|---------|--------|
| `GatewayContext` | Entry point to Gateway services | Works |
| `TagManager` | Tag read/write/browse operations | Works |
| `TagProvider` | Provider-specific tag operations | Works |
| `QualifiedPath` | Tag path construction | Works |
| `SystemManager.getDataDir()` | Get Ignition data directory | Works |

---

## Filesystem Approach (Ignition 8.3+)

Since Ignition 8.3, project resources are stored as human-readable JSON files in the Ignition data directory. This enables direct filesystem access for CRUD operations.

### Advantages

1. **Full CRUD Support**: All operations work without internal SDK classes
2. **Transparency**: JSON files are easy to inspect and debug
3. **Compatibility**: Works with standard Java NIO APIs
4. **Performance**: Direct file I/O is efficient

### Limitations

1. **Project Scan Required**: Changes made via filesystem may not be immediately visible in Designer. A project resource scan or Gateway restart may be needed.
2. **File Permissions**: Requires write access to Ignition data directory
3. **No Transactions**: Filesystem operations are not atomic with Ignition's internal project management

### Data Directory Location

The handler automatically detects the Ignition data directory:
1. From `GatewayContext.getSystemManager().getDataDir()`
2. From `IGNITION_HOME` environment variable
3. Windows default: `C:\Program Files\Inductive Automation\Ignition\data`
4. Linux default: `/usr/local/ignition/data`

---

## API Endpoint Summary

| Endpoint | Method | Status |
|----------|--------|--------|
| `/system/llm-gateway/health` | GET | Working |
| `/system/llm-gateway/api/v1/chat` | POST | Working |
| `/system/llm-gateway/api/v1/action` | POST | Working |
| Tag operations | - | Full CRUD |
| View operations | - | Full CRUD (filesystem) |
| Script operations | - | Full CRUD (filesystem) |
| Named Query operations | - | Full CRUD (filesystem) |

---

## Usage Examples

### List Views in a Project

```bash
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "action": "read",
    "resourceType": "perspective-view",
    "resourcePath": "MainProject/*"
  }'
```

### Create a Perspective View

```bash
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "550e8400-e29b-41d4-a716-446655440001",
    "action": "create",
    "resourceType": "perspective-view",
    "resourcePath": "MainProject/Views/NewView",
    "payload": {
      "root": {
        "type": "ia.container.flex",
        "children": []
      }
    }
  }'
```

### List Scripts in a Project

```bash
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "550e8400-e29b-41d4-a716-446655440002",
    "action": "read",
    "resourceType": "script",
    "resourcePath": "MainProject/*"
  }'
```

### Create a Named Query

```bash
curl -X POST http://localhost:8088/system/llm-gateway/api/v1/action \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "550e8400-e29b-41d4-a716-446655440003",
    "action": "create",
    "resourceType": "named-query",
    "resourcePath": "MainProject/GetAllAlarms",
    "payload": {
      "query": "SELECT * FROM alarms WHERE active = 1",
      "queryType": "Select",
      "database": "main_db"
    }
  }'
```

---

## Build Information

**Final Build Output:**
```
build/target/LLM-Gateway-unsigned.modl
```

**Compilation Status:** All errors fixed
- Removed unavailable SDK class imports
- Implemented filesystem-based CRUD for all handlers
- Added proper exception handling

---

## Files Modified

| File | Changes |
|------|---------|
| `ViewResourceHandler.java` | Full filesystem-based CRUD implementation |
| `ScriptResourceHandler.java` | Full filesystem-based CRUD implementation |
| `NamedQueryResourceHandler.java` | Full filesystem-based CRUD implementation |

---

## Recommendations

1. **Project Sync**: After creating/modifying resources via the API, trigger a project resource scan in Designer or restart the Gateway for changes to appear.

2. **Backups**: Consider implementing backup functionality before destructive operations.

3. **Testing**: Test in development environment before using in production.

4. **Permissions**: Ensure the Gateway process has write access to the projects directory.

---

## Conclusion

The LLM Gateway module now provides **full CRUD functionality** for all resource types:
- **Tags**: Via Ignition TagManager API
- **Views, Scripts, Named Queries**: Via direct filesystem access

The module is ready for deployment and testing.
