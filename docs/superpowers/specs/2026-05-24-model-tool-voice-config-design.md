# Model, Tool, and Voice Configuration Design

Date: 2026-05-24
Status: Approved design draft
Scope: Configuration loop for model/tool/voice capabilities, plus real text and embedding adapters. Voice, JsonRPC, and MCP use extensible runtime interfaces with mock implementations in this phase.

## Goals

Add tenant-scoped configuration for model providers, custom tools, chat runtime model switching, agent model/tool binding, and voice interaction. The implementation should preserve the current Spring Boot + React product shape while moving backend persistence to Spring Data JPA.

This phase must support:

- CRUD for user-configured model entries, including text, reasoning, embedding, speech-to-text, text-to-speech, and multimodal model types.
- CRUD for user-configured tools, including built-in tools, JsonRPC tools, and MCP tools.
- Model and tool selection from chat and agent creation flows.
- Per-chat model switching and reasoning effort selection.
- Browser recording upload, backend speech-to-text adapter flow, and frontend playback of backend-generated audio.
- Real text model and embedding adapter path for compatible OpenAI-style providers such as DeepSeek.
- Mock/extensible adapters for voice, JsonRPC, and MCP until provider-specific details are chosen.

## Non-Goals

- Full production MCP client protocol coverage.
- Full production TTS/STT vendor integrations.
- Fine-grained RBAC for individual model/tool entries.
- Billing, quota enforcement, or model cost accounting.
- Migrating pgvector similarity search to pure JPA. Vector search remains a narrow custom query boundary.

## Persistence And ORM

The backend will use `spring-boot-starter-data-jpa` for business CRUD. Repository methods should prefer Spring Data named queries such as:

- `findByTenantIdAndId(...)`
- `findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(...)`
- `existsByTenantIdAndName(...)`
- `deleteByTenantIdAndId(...)`

Handwritten SQL should be avoided for normal CRUD. The allowed exception is vector similarity retrieval, because pgvector distance operators and casts are outside JPA's normal derived-query model. That exception should be isolated behind a small RAG custom repository or adapter.

The existing Flyway schema remains the source of database structure. New migrations add tables and references; JPA entities map onto those tables rather than generating schema implicitly.

## Database Model

### `model_configs`

Tenant-scoped model registry.

Columns:

- `id uuid primary key`
- `tenant_id varchar(64) not null default 'default'`
- `name varchar(128) not null`
- `provider varchar(64) not null`
- `model_type varchar(32) not null`
- `model_name varchar(128) not null`
- `base_url text`
- `api_key_ref text`
- `default_temperature numeric(4,3)`
- `default_max_tokens int`
- `default_reasoning_effort varchar(32)`
- `embedding_dim int`
- `input_modalities jsonb not null default '[]'`
- `output_modalities jsonb not null default '[]'`
- `extra_params jsonb not null default '{}'`
- `enabled boolean not null default true`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

Enums in Java:

- `ModelType`: `TEXT`, `REASONING`, `EMBEDDING`, `STT`, `TTS`, `MULTIMODAL`
- `ReasoningEffort`: `LOW`, `MEDIUM`, `HIGH`

### `tool_configs`

Tenant-scoped tool registry.

Columns:

- `id uuid primary key`
- `tenant_id varchar(64) not null default 'default'`
- `name varchar(128) not null`
- `description text`
- `tool_type varchar(32) not null`
- `endpoint text`
- `auth_type varchar(32)`
- `auth_ref text`
- `input_schema jsonb not null default '{}'`
- `config jsonb not null default '{}'`
- `timeout_ms int not null default 30000`
- `enabled boolean not null default true`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

Enums in Java:

- `ToolType`: `BUILTIN`, `JSON_RPC`, `MCP`
- `ToolAuthType`: `NONE`, `BEARER`, `BASIC`, `API_KEY`

### Agent References

`agent_configs` should reference configured models instead of storing only raw model names.

New columns:

- `primary_model_id uuid references model_configs(id)`
- `reasoning_model_id uuid references model_configs(id)`
- `embedding_model_id uuid references model_configs(id)`
- `default_reasoning_effort varchar(32)`

New join table:

- `agent_tool_bindings(agent_config_id uuid, tool_config_id uuid, tenant_id varchar(64), created_at timestamptz)`

The existing `tools jsonb` column can remain temporarily for backward compatibility, but new code should use `agent_tool_bindings`.

### Voice Artifacts

`audio_artifacts` stores uploaded recordings and generated speech outputs.

Columns:

- `id uuid primary key`
- `tenant_id varchar(64) not null default 'default'`
- `conversation_id uuid references conversations(id)`
- `message_id uuid references chat_messages(id)`
- `direction varchar(16) not null`
- `storage_key text not null`
- `mime_type varchar(128) not null`
- `duration_ms int`
- `transcript text`
- `model_config_id uuid references model_configs(id)`
- `created_at timestamptz not null default now()`

`direction`: `INPUT` for user recording, `OUTPUT` for generated TTS.

## Backend Components

### Model Configuration Module

Package: `com.agentscope.demo.model`

Responsibilities:

- Validate model type and provider-specific fields.
- Hide raw API keys behind `apiKeyRef`; local development may store environment variable names such as `DEEPSEEK_APIKEY`.
- Provide model lists filtered by type and enabled state.

Primary API:

- `GET /api/model-configs`
- `GET /api/model-configs?type=TEXT`
- `POST /api/model-configs`
- `PUT /api/model-configs/{id}`
- `DELETE /api/model-configs/{id}`

JPA repository examples:

- `List<ModelConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId)`
- `List<ModelConfigEntity> findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(String tenantId, ModelType type)`
- `Optional<ModelConfigEntity> findByTenantIdAndId(String tenantId, UUID id)`
- `void deleteByTenantIdAndId(String tenantId, UUID id)`

### Tool Configuration Module

Package: `com.agentscope.demo.tool`

Responsibilities:

- Store tool metadata and invocation schema.
- Expose configured tools for chat and agent creation.
- Create runtime invokers from `ToolConfigEntity`.

Primary API:

- `GET /api/tool-configs`
- `GET /api/tool-configs?type=MCP`
- `POST /api/tool-configs`
- `PUT /api/tool-configs/{id}`
- `DELETE /api/tool-configs/{id}`
- `POST /api/tool-configs/{id}/test`

Runtime adapters:

- `BuiltinToolInvoker`
- `JsonRpcToolInvoker` as mock/extensible in this phase
- `McpToolInvoker` as mock/extensible in this phase

### Chat Runtime Selection

`ChatRequest` gains:

- `modelId`
- `reasoningEffort`
- `toolIds`
- `sttModelId`
- `ttsModelId`
- `audioArtifactId`

Resolution order:

1. Request-level model/tool overrides.
2. Selected agent configuration.
3. Tenant default enabled text model.
4. Development fallback mock model.

Reasoning effort applies only when the resolved model type is `REASONING` or the provider accepts a reasoning-style option. If a normal text model is selected, the backend should ignore the field and emit a collapsed warning event rather than failing the request.

### Agent Creation And Runtime

Agent creation should use configured model/tool/knowledge references:

- User selects primary model.
- User optionally selects reasoning model and default reasoning effort.
- User selects embedding model for RAG behavior if different from KB default.
- User binds one or more tools from `tool_configs`.
- User binds one or more knowledge bases.

At runtime, `AgentRuntimeFactory` resolves `AgentConfigEntity` into a runtime `AgentConfig` object using model and tool repositories. AgentScope receives the configured model adapter, toolkit, and RAG context.

### Voice Module

Package: `com.agentscope.demo.voice`

Primary API:

- `POST /api/voice/transcriptions` multipart audio upload with optional `sttModelId`.
- `POST /api/voice/speech` with text and optional `ttsModelId`.
- `GET /api/audio-artifacts/{id}` metadata.
- `GET /api/audio-artifacts/{id}/content` audio stream.

Adapters:

- `SpeechToTextClient`
- `TextToSpeechClient`
- `MockSpeechToTextClient`
- `MockTextToSpeechClient`

In this phase, mock voice clients are acceptable. They must still exercise storage, metadata persistence, upload, and playback flows.

## Frontend Design

### Navigation

The app sidebar gains a compact settings area:

- `Models`
- `Tools`
- `Agents`
- `Knowledge`

### Models Page

Model cards/table display:

- Name
- Provider
- Type
- Model name
- Enabled state
- Default parameters

Create/edit form fields:

- Type selector
- Provider selector
- Base URL
- Model name
- API key reference
- Temperature / max tokens where relevant
- Reasoning effort where relevant
- Embedding dimension where relevant
- Modalities where relevant

### Tools Page

Tool cards/table display:

- Name
- Type
- Endpoint
- Enabled state
- Timeout

Create/edit form fields:

- Tool type
- Description
- Endpoint
- Auth type/reference
- Input JSON schema
- Extra config JSON
- Test button

### Agent Creation

Agent editor includes:

- Primary model select.
- Optional reasoning model select.
- Default reasoning effort select.
- Tool multi-select.
- Knowledge base multi-select.
- Existing system prompt and model parameter fields.

### Chat Input

The chat composer includes:

- Model select for current conversation turn.
- Reasoning effort select shown when the selected model supports reasoning.
- Tool selection popover for temporary tool overrides.
- Record button using `MediaRecorder`.
- Upload state and STT transcript insertion.
- Audio playback component for messages that include output audio.

SSE rendering remains unchanged for collapsed intermediate state behavior. New event payloads may include:

- `model_resolved`
- `tool_invocation`
- `voice_transcription`
- `audio_output`

Non-final operational events stay collapsed by default.

## Error Handling

- Missing configured model: fall back to tenant default, then mock model in dev.
- Disabled model/tool selected: return validation error before execution.
- Reasoning effort on non-reasoning model: continue and emit collapsed warning.
- Invalid JsonRPC/MCP config: save is allowed only if JSON schema/config validates structurally; live connectivity can fail in test endpoint.
- Audio upload too large or unsupported type: reject with `400`.
- STT/TTS adapter failure: persist failed artifact state where applicable and emit chat error event.

## Testing Strategy

Backend tests:

- Model config service CRUD and tenant isolation.
- Tool config service CRUD and tenant isolation.
- Chat request resolves override model before agent/default model.
- Reasoning effort warning for non-reasoning model.
- Agent config binds model and tool ids.
- Voice upload stores an input artifact and returns mock transcript.
- TTS stores output artifact and returns playable URL.
- pgvector custom search remains covered by existing RAG tests.

Frontend tests:

- SSE merge still collapses intermediate events.
- Model selector changes outgoing chat request `modelId`.
- Reasoning effort selector appears only for reasoning models.
- Tool multi-select sends `toolIds`.
- Recorder upload inserts returned transcript.
- Audio output renders playback controls.

Smoke test:

1. Create text model config using DeepSeek-compatible settings.
2. Create embedding model config.
3. Create JsonRPC tool config with mock/test endpoint.
4. Create agent using selected model and tool.
5. Send simple chat with explicit model override.
6. Send complex chat through agent and verify task events.
7. Upload audio and receive mock transcript.
8. Request TTS and play/stream returned audio content.

## Implementation Order

1. Add JPA dependency and configure entities/repositories while preserving Flyway.
2. Add migrations for model configs, tool configs, agent bindings, and audio artifacts.
3. Implement model config CRUD.
4. Implement tool config CRUD and test endpoint.
5. Add chat runtime model/tool override resolution.
6. Add agent creation model/tool binding APIs.
7. Add voice upload/TTS APIs with mock clients and storage.
8. Add frontend settings pages and chat composer controls.
9. Run backend tests, frontend tests, build, Docker-backed smoke test.

## Open Decisions

All decisions required for this phase are fixed:

- Scope is option B.
- CRUD persistence uses Spring Data JPA and named repository methods where practical.
- Real adapter work covers text and embedding models.
- Voice, JsonRPC, and MCP get extensible interfaces and mock runtime behavior in this phase.
- pgvector similarity search may remain a custom query boundary.
