# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-12-19

### Fixed
- **Chat-based View/Script/Named Query creation** - Fixed validation error that prevented creating these resources via natural language chat
  - `ActionParser.java`: Changed resource type from `"view"` to `"perspective-view"` to match validator expectations
  - `ActionSchemas.java`: Added 10 missing tool schemas for script and named query operations

### Added

#### Core Features
- Natural language chat interface for Ignition Gateway management
- Direct action execution API for structured CRUD operations
- Multi-turn conversation support with context preservation
- Streaming responses via Server-Sent Events (SSE)

#### Resource Handlers
- **Tag Handler**: Full CRUD via Ignition TagManager API
  - Browse tag providers and folders
  - Create/read/update/delete tags
  - Support for all tag types (AtomicTag, UDT, Folder)
  - Tag value read/write operations
- **View Handler**: Full CRUD via filesystem (Ignition 8.3+)
  - List Perspective views in projects
  - Create views with JSON configuration
  - Update view.json content
  - Delete views with force confirmation
- **Script Handler**: Full CRUD via filesystem (Ignition 8.3+)
  - List project library scripts
  - Create scripts with code.py and resource.json
  - Update script content
  - Delete scripts with force confirmation
  - Security: Blocked dangerous patterns (os.system, eval, exec)
- **Named Query Handler**: Full CRUD via filesystem (Ignition 8.3+)
  - List named queries in projects
  - Create queries with SQL and configuration
  - Update query content and parameters
  - Delete queries with force confirmation
  - Security: SQL injection prevention checks
- **Project Handler**: Read-only project listing

#### LLM Providers
- **Claude (Anthropic)**: Full support with excellent tool calling
- **OpenAI**: GPT-4o support with tool calling
- **Ollama**: Local model support (llama3.1, llama3.2)

#### Security
- API key authentication with SHA-256 hashing
- Granular permission system (17 distinct permissions)
- Force confirmation required for destructive operations
- Audit logging with correlation IDs
- Environment modes (development/test/production)
- Rate limiting infrastructure (not yet enforced)

#### API Endpoints
- `GET /system/llm-gateway/health` - Health check
- `POST /system/llm-gateway/api/v1/chat` - Natural language chat
- `POST /system/llm-gateway/api/v1/chat/stream` - Streaming chat (SSE)
- `POST /system/llm-gateway/api/v1/action` - Direct action execution
- `GET /system/llm-gateway/admin/providers` - List LLM providers
- `POST /system/llm-gateway/admin/providers/{id}/config` - Configure provider
- `GET/POST/DELETE /system/llm-gateway/admin/api-keys` - Manage API keys

### Security Notes
- API keys are stored with SHA-256 hashing (only prefix shown after creation)
- Destructive operations require `force: true` option
- Script handler blocks dangerous Python patterns
- Named query handler includes SQL injection prevention

### Known Limitations
- View/Script/Named Query operations require Ignition 8.3+ (filesystem-based storage)
- Changes made via filesystem may require project scan or Gateway restart to appear in Designer
- Rate limiting infrastructure is present but not yet enforced

### Technical Details
- Built with Ignition SDK
- Java 17+ required
- Maven 3.8+ for building
- Module file: `build/target/LLM-Gateway-unsigned.modl`
