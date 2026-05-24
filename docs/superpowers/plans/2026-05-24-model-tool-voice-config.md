# Model Tool Voice Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add tenant-scoped model/tool/voice configuration, use Spring Data JPA for CRUD, let chat and agent flows select configured models/tools, and provide real text/embedding adapter seams with mock voice/JsonRPC/MCP adapters.

**Architecture:** Flyway remains the schema authority. Business CRUD moves to Spring Data JPA entities and named repositories, while pgvector similarity search remains isolated as a custom RAG boundary. The backend exposes configuration APIs and runtime resolver services; the frontend adds settings views and chat composer controls that pass selected model/tool/voice options to existing SSE execution.

**Tech Stack:** Java 21, Spring Boot 4 WebFlux, Spring Data JPA, Flyway, PostgreSQL + pgvector, Redis, AgentScope-Java, React + Vite + TypeScript + Tailwind, Vitest.

---

## File Structure

Backend files to create:

- `backend/src/main/resources/db/migration/V2__model_tool_voice_config.sql`: model/tool/agent/audio schema additions.
- `backend/src/main/java/com/agentscope/demo/common/JpaJsonConfig.java`: JPA JSON converter helpers if needed.
- `backend/src/main/java/com/agentscope/demo/model/*`: model config entity, enums, DTOs, repository, service, controller, runtime clients.
- `backend/src/main/java/com/agentscope/demo/toolconfig/*`: tool config entity, enums, DTOs, repository, service, controller, invoker interfaces.
- `backend/src/main/java/com/agentscope/demo/agent/*`: JPA agent config entity and repository for model/tool/KB bindings.
- `backend/src/main/java/com/agentscope/demo/voice/*`: audio artifact entity, repository, DTOs, controller, STT/TTS clients.
- `backend/src/test/java/com/agentscope/demo/model/*`: model config and runtime resolution tests.
- `backend/src/test/java/com/agentscope/demo/toolconfig/*`: tool config CRUD and test invocation tests.
- `backend/src/test/java/com/agentscope/demo/voice/*`: audio upload/speech tests.

Backend files to modify:

- `backend/pom.xml`: replace Spring Data JDBC dependency with Spring Data JPA.
- `backend/src/main/resources/application-dev.yml`: set JPA validation mode and preserve Flyway.
- `backend/src/main/java/com/agentscope/demo/chat/ChatRequest.java`: add model/tool/voice selection fields.
- `backend/src/main/java/com/agentscope/demo/chat/HybridChatExecutionService.java`: resolve request/agent/default models and tools.
- `backend/src/main/java/com/agentscope/demo/chat/AgentConfig.java`: carry model config and tool config ids.
- `backend/src/main/java/com/agentscope/demo/chat/AgentRuntimeFactory.java`: build runtime model/toolkit from resolved config.
- Existing JDBC repositories: migrate normal CRUD to JPA repositories, leaving pgvector custom insertion/search isolated.

Frontend files to create:

- `frontend/src/lib/api.ts`: typed REST helper functions.
- `frontend/src/types/config.ts`: model/tool/agent/voice request types.
- `frontend/src/components/ModelSettings.tsx`: model registry UI.
- `frontend/src/components/ToolSettings.tsx`: tool registry UI.
- `frontend/src/components/AgentSettings.tsx`: agent binding UI.
- `frontend/src/components/ChatComposer.tsx`: chat input with model/tool/reasoning/record controls.
- `frontend/src/components/AudioMessage.tsx`: playback component.

Frontend files to modify:

- `frontend/src/App.tsx`: add settings navigation and wire composer.
- `frontend/src/lib/stream.ts`: labels for new SSE event types.
- `frontend/src/components/ChatStreamBlocks.tsx`: render audio event payloads.
- Existing Vitest files plus new component tests.

---

## Task 1: Add JPA Dependency And Schema Migration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/db/migration/V2__model_tool_voice_config.sql`

- [ ] **Step 1: Write a failing schema-oriented repository test**

Create `backend/src/test/java/com/agentscope/demo/model/ModelConfigRepositoryContractTest.java`:

```java
package com.agentscope.demo.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelConfigRepositoryContractTest {

    @Test
    void namedRepositoryMethodsAreAvailableForTenantCrud() throws Exception {
        assertThat(ModelConfigRepository.class.getMethod("findByTenantIdAndId", String.class, UUID.class)).isNotNull();
        assertThat(ModelConfigRepository.class.getMethod("deleteByTenantIdAndId", String.class, UUID.class)).isNotNull();
        assertThat(ModelConfigRepository.class.getMethod("findByTenantIdAndEnabledTrueOrderByCreatedAtDesc", String.class)).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd /Users/stevelin/IdeaProjects/agentscope-demo/backend
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ModelConfigRepositoryContractTest test
```

Expected: compilation fails because `ModelConfigRepository` does not exist.

- [ ] **Step 3: Switch dependency from JDBC to JPA**

In `backend/pom.xml`, replace:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
```

with:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

- [ ] **Step 4: Add JPA validation configuration**

In `backend/src/main/resources/application-dev.yml`, add under `spring:`:

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

- [ ] **Step 5: Add migration**

Create `backend/src/main/resources/db/migration/V2__model_tool_voice_config.sql`:

```sql
CREATE TABLE IF NOT EXISTS model_configs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    name varchar(128) NOT NULL,
    provider varchar(64) NOT NULL,
    model_type varchar(32) NOT NULL,
    model_name varchar(128) NOT NULL,
    base_url text,
    api_key_ref text,
    default_temperature numeric(4,3),
    default_max_tokens int,
    default_reasoning_effort varchar(32),
    embedding_dim int,
    input_modalities jsonb NOT NULL DEFAULT '[]',
    output_modalities jsonb NOT NULL DEFAULT '[]',
    extra_params jsonb NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS tool_configs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    name varchar(128) NOT NULL,
    description text,
    tool_type varchar(32) NOT NULL,
    endpoint text,
    auth_type varchar(32),
    auth_ref text,
    input_schema jsonb NOT NULL DEFAULT '{}',
    config jsonb NOT NULL DEFAULT '{}',
    timeout_ms int NOT NULL DEFAULT 30000,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

ALTER TABLE agent_configs
    ADD COLUMN IF NOT EXISTS primary_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS reasoning_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS embedding_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS default_reasoning_effort varchar(32);

CREATE TABLE IF NOT EXISTS agent_tool_bindings (
    agent_config_id uuid NOT NULL REFERENCES agent_configs(id) ON DELETE CASCADE,
    tool_config_id uuid NOT NULL REFERENCES tool_configs(id) ON DELETE CASCADE,
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (agent_config_id, tool_config_id)
);

CREATE TABLE IF NOT EXISTS audio_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    conversation_id uuid REFERENCES conversations(id),
    message_id uuid REFERENCES chat_messages(id),
    direction varchar(16) NOT NULL,
    storage_key text NOT NULL,
    mime_type varchar(128) NOT NULL,
    duration_ms int,
    transcript text,
    model_config_id uuid REFERENCES model_configs(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_model_configs_tenant_type ON model_configs(tenant_id, model_type);
CREATE INDEX IF NOT EXISTS idx_tool_configs_tenant_type ON tool_configs(tenant_id, tool_type);
CREATE INDEX IF NOT EXISTS idx_audio_artifacts_tenant_conversation ON audio_artifacts(tenant_id, conversation_id);
```

- [ ] **Step 6: Run focused test**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ModelConfigRepositoryContractTest test
```

Expected: still fails because entity/repository are not implemented. This is correct; Task 2 makes it pass.

---

## Task 2: Implement Model Config JPA CRUD

**Files:**
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelType.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ReasoningEffort.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigEntity.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigRepository.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigRequest.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigResponse.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigService.java`
- Create: `backend/src/main/java/com/agentscope/demo/model/ModelConfigController.java`
- Test: `backend/src/test/java/com/agentscope/demo/model/ModelConfigServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/com/agentscope/demo/model/ModelConfigServiceTest.java`:

```java
package com.agentscope.demo.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentscope.demo.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelConfigServiceTest {

    @Test
    void createsAndListsEnabledModelsByTenantAndType() {
        RecordingModelConfigRepository repository = new RecordingModelConfigRepository();
        ModelConfigService service = new ModelConfigService(repository);

        TenantContext.runWithTenant("tenant-a", () -> service.create(new ModelConfigRequest(
                "DeepSeek Chat", "deepseek", ModelType.TEXT, "deepseek-chat",
                "https://api.deepseek.com", "DEEPSEEK_APIKEY", 0.7, 4096,
                null, null, List.of("text"), List.of("text"), "{}",
                true
        )));

        TenantContext.runWithTenant("tenant-b", () -> service.create(new ModelConfigRequest(
                "Other", "mock", ModelType.TEXT, "mock-chat",
                null, null, null, null, null, null,
                List.of("text"), List.of("text"), "{}", true
        )));

        TenantContext.runWithTenant("tenant-a", () -> {
            List<ModelConfigResponse> models = service.list(ModelType.TEXT);
            assertThat(models).hasSize(1);
            assertThat(models.getFirst().tenantId()).isEqualTo("tenant-a");
            assertThat(models.getFirst().name()).isEqualTo("DeepSeek Chat");
        });
    }

    @Test
    void rejectsDuplicateNameWithinTenant() {
        RecordingModelConfigRepository repository = new RecordingModelConfigRepository();
        ModelConfigService service = new ModelConfigService(repository);
        ModelConfigRequest request = new ModelConfigRequest(
                "DeepSeek Chat", "deepseek", ModelType.TEXT, "deepseek-chat",
                null, "DEEPSEEK_APIKEY", null, null, null, null,
                List.of("text"), List.of("text"), "{}", true
        );

        TenantContext.runWithTenant("tenant-a", () -> service.create(request));

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
        );
    }

    private static class RecordingModelConfigRepository implements ModelConfigRepository {
        private final List<ModelConfigEntity> models = new ArrayList<>();

        @Override
        public boolean existsByTenantIdAndName(String tenantId, String name) {
            return models.stream().anyMatch(model -> model.tenantId().equals(tenantId) && model.name().equals(name));
        }

        @Override
        public ModelConfigEntity save(ModelConfigEntity entity) {
            models.removeIf(model -> model.id().equals(entity.id()));
            models.add(entity);
            return entity;
        }

        @Override
        public List<ModelConfigEntity> findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(String tenantId, ModelType modelType) {
            return models.stream()
                    .filter(model -> model.tenantId().equals(tenantId))
                    .filter(model -> model.modelType() == modelType)
                    .filter(ModelConfigEntity::enabled)
                    .toList();
        }

        @Override
        public List<ModelConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId) {
            return models.stream().filter(model -> model.tenantId().equals(tenantId)).filter(ModelConfigEntity::enabled).toList();
        }

        @Override
        public Optional<ModelConfigEntity> findByTenantIdAndId(String tenantId, UUID id) {
            return models.stream().filter(model -> model.tenantId().equals(tenantId) && model.id().equals(id)).findFirst();
        }

        @Override
        public void deleteByTenantIdAndId(String tenantId, UUID id) {
            models.removeIf(model -> model.tenantId().equals(tenantId) && model.id().equals(id));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ModelConfigServiceTest,ModelConfigRepositoryContractTest test
```

Expected: compilation fails because model package types do not exist.

- [ ] **Step 3: Add model enums and records**

Create `ModelType.java`:

```java
package com.agentscope.demo.model;

public enum ModelType {
    TEXT,
    REASONING,
    EMBEDDING,
    STT,
    TTS,
    MULTIMODAL
}
```

Create `ReasoningEffort.java`:

```java
package com.agentscope.demo.model;

public enum ReasoningEffort {
    LOW,
    MEDIUM,
    HIGH
}
```

Create `ModelConfigRequest.java`:

```java
package com.agentscope.demo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ModelConfigRequest(
        @NotBlank String name,
        @NotBlank String provider,
        @NotNull ModelType modelType,
        @NotBlank String modelName,
        String baseUrl,
        String apiKeyRef,
        Double defaultTemperature,
        Integer defaultMaxTokens,
        ReasoningEffort defaultReasoningEffort,
        Integer embeddingDim,
        List<String> inputModalities,
        List<String> outputModalities,
        String extraParams,
        boolean enabled
) {
}
```

Create `ModelConfigResponse.java`:

```java
package com.agentscope.demo.model;

import java.util.List;
import java.util.UUID;

public record ModelConfigResponse(
        UUID id,
        String tenantId,
        String name,
        String provider,
        ModelType modelType,
        String modelName,
        String baseUrl,
        String apiKeyRef,
        Double defaultTemperature,
        Integer defaultMaxTokens,
        ReasoningEffort defaultReasoningEffort,
        Integer embeddingDim,
        List<String> inputModalities,
        List<String> outputModalities,
        String extraParams,
        boolean enabled
) {
}
```

- [ ] **Step 4: Add JPA entity**

Create `ModelConfigEntity.java`:

```java
package com.agentscope.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_configs")
public class ModelConfigEntity {
    @Id
    private UUID id;
    private String tenantId;
    private String name;
    private String provider;
    @Enumerated(EnumType.STRING)
    private ModelType modelType;
    private String modelName;
    private String baseUrl;
    private String apiKeyRef;
    private Double defaultTemperature;
    private Integer defaultMaxTokens;
    @Enumerated(EnumType.STRING)
    private ReasoningEffort defaultReasoningEffort;
    private Integer embeddingDim;
    @Column(columnDefinition = "jsonb")
    private String inputModalities;
    @Column(columnDefinition = "jsonb")
    private String outputModalities;
    @Column(columnDefinition = "jsonb")
    private String extraParams;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    protected ModelConfigEntity() {
    }

    public ModelConfigEntity(UUID id, String tenantId, String name, String provider, ModelType modelType,
                             String modelName, String baseUrl, String apiKeyRef, Double defaultTemperature,
                             Integer defaultMaxTokens, ReasoningEffort defaultReasoningEffort, Integer embeddingDim,
                             String inputModalities, String outputModalities, String extraParams, boolean enabled,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.provider = provider;
        this.modelType = modelType;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.apiKeyRef = apiKeyRef;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultReasoningEffort = defaultReasoningEffort;
        this.embeddingDim = embeddingDim;
        this.inputModalities = inputModalities;
        this.outputModalities = outputModalities;
        this.extraParams = extraParams;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String name() { return name; }
    public String provider() { return provider; }
    public ModelType modelType() { return modelType; }
    public String modelName() { return modelName; }
    public String baseUrl() { return baseUrl; }
    public String apiKeyRef() { return apiKeyRef; }
    public Double defaultTemperature() { return defaultTemperature; }
    public Integer defaultMaxTokens() { return defaultMaxTokens; }
    public ReasoningEffort defaultReasoningEffort() { return defaultReasoningEffort; }
    public Integer embeddingDim() { return embeddingDim; }
    public String inputModalities() { return inputModalities; }
    public String outputModalities() { return outputModalities; }
    public String extraParams() { return extraParams; }
    public boolean enabled() { return enabled; }
}
```

- [ ] **Step 5: Add repository interface with named methods**

Create `ModelConfigRepository.java`:

```java
package com.agentscope.demo.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, UUID> {
    boolean existsByTenantIdAndName(String tenantId, String name);
    List<ModelConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId);
    List<ModelConfigEntity> findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(String tenantId, ModelType modelType);
    Optional<ModelConfigEntity> findByTenantIdAndId(String tenantId, UUID id);
    void deleteByTenantIdAndId(String tenantId, UUID id);
}
```

- [ ] **Step 6: Add service and controller**

Create `ModelConfigService.java`:

```java
package com.agentscope.demo.model;

import com.agentscope.demo.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {
    private final ModelConfigRepository repository;

    public ModelConfigService(ModelConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ModelConfigResponse create(ModelConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        if (repository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new IllegalArgumentException("Model config name already exists in tenant");
        }
        Instant now = Instant.now();
        ModelConfigEntity saved = repository.save(new ModelConfigEntity(
                UUID.randomUUID(), tenantId, request.name(), request.provider(), request.modelType(), request.modelName(),
                request.baseUrl(), request.apiKeyRef(), request.defaultTemperature(), request.defaultMaxTokens(),
                request.defaultReasoningEffort(), request.embeddingDim(), toJsonArray(request.inputModalities()),
                toJsonArray(request.outputModalities()), request.extraParams() == null ? "{}" : request.extraParams(),
                request.enabled(), now, now
        ));
        return toResponse(saved);
    }

    public List<ModelConfigResponse> list(ModelType type) {
        String tenantId = TenantContext.currentTenantId();
        List<ModelConfigEntity> entities = type == null
                ? repository.findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(tenantId)
                : repository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(tenantId, type);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteByTenantIdAndId(TenantContext.currentTenantId(), id);
    }

    private ModelConfigResponse toResponse(ModelConfigEntity entity) {
        return new ModelConfigResponse(entity.id(), entity.tenantId(), entity.name(), entity.provider(), entity.modelType(),
                entity.modelName(), entity.baseUrl(), entity.apiKeyRef(), entity.defaultTemperature(),
                entity.defaultMaxTokens(), entity.defaultReasoningEffort(), entity.embeddingDim(),
                List.of(), List.of(), entity.extraParams(), entity.enabled());
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", values) + "\"]";
    }
}
```

Create `ModelConfigController.java`:

```java
package com.agentscope.demo.model;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {
    private final ModelConfigService service;

    public ModelConfigController(ModelConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModelConfigResponse> list(@RequestParam(required = false) ModelType type) {
        return service.list(type);
    }

    @PostMapping
    public ModelConfigResponse create(@Valid @RequestBody ModelConfigRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ModelConfigServiceTest,ModelConfigRepositoryContractTest test
```

Expected: PASS.

---

## Task 3: Implement Tool Config CRUD And Mock Invocation

**Files:**
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolType.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolAuthType.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigEntity.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigRepository.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigRequest.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigResponse.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigService.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolConfigController.java`
- Create: `backend/src/main/java/com/agentscope/demo/toolconfig/ToolTestResponse.java`
- Test: `backend/src/test/java/com/agentscope/demo/toolconfig/ToolConfigServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `ToolConfigServiceTest.java`:

```java
package com.agentscope.demo.toolconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolConfigServiceTest {

    @Test
    void createsAndListsToolsByTenantAndType() {
        RecordingToolConfigRepository repository = new RecordingToolConfigRepository();
        ToolConfigService service = new ToolConfigService(repository);

        TenantContext.runWithTenant("tenant-a", () -> service.create(new ToolConfigRequest(
                "Search RPC", "Search through RPC", ToolType.JSON_RPC, "http://localhost:9009/rpc",
                ToolAuthType.NONE, null, "{\"type\":\"object\"}", "{}", 5000, true
        )));
        TenantContext.runWithTenant("tenant-b", () -> service.create(new ToolConfigRequest(
                "MCP", "MCP test", ToolType.MCP, "stdio://mock",
                ToolAuthType.NONE, null, "{}", "{}", 30000, true
        )));

        TenantContext.runWithTenant("tenant-a", () -> {
            List<ToolConfigResponse> tools = service.list(ToolType.JSON_RPC);
            assertThat(tools).hasSize(1);
            assertThat(tools.getFirst().tenantId()).isEqualTo("tenant-a");
            assertThat(tools.getFirst().name()).isEqualTo("Search RPC");
        });
    }

    @Test
    void testEndpointReturnsMockObservation() {
        RecordingToolConfigRepository repository = new RecordingToolConfigRepository();
        ToolConfigService service = new ToolConfigService(repository);
        UUID id = TenantContext.runWithTenant("tenant-a", () -> service.create(new ToolConfigRequest(
                "MCP", "MCP test", ToolType.MCP, "stdio://mock",
                ToolAuthType.NONE, null, "{}", "{}", 30000, true
        )).id());

        TenantContext.runWithTenant("tenant-a", () -> {
            ToolTestResponse response = service.test(id);
            assertThat(response.success()).isTrue();
            assertThat(response.observation()).contains("mock");
        });
    }

    private static class RecordingToolConfigRepository implements ToolConfigRepository {
        private final List<ToolConfigEntity> tools = new ArrayList<>();
        public boolean existsByTenantIdAndName(String tenantId, String name) { return false; }
        public ToolConfigEntity save(ToolConfigEntity entity) { tools.removeIf(tool -> tool.id().equals(entity.id())); tools.add(entity); return entity; }
        public List<ToolConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId) { return tools.stream().filter(tool -> tool.tenantId().equals(tenantId)).toList(); }
        public List<ToolConfigEntity> findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc(String tenantId, ToolType type) { return tools.stream().filter(tool -> tool.tenantId().equals(tenantId) && tool.toolType() == type).toList(); }
        public Optional<ToolConfigEntity> findByTenantIdAndId(String tenantId, UUID id) { return tools.stream().filter(tool -> tool.tenantId().equals(tenantId) && tool.id().equals(id)).findFirst(); }
        public void deleteByTenantIdAndId(String tenantId, UUID id) { tools.removeIf(tool -> tool.tenantId().equals(tenantId) && tool.id().equals(id)); }
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ToolConfigServiceTest test
```

Expected: compilation fails because tool config types do not exist.

- [ ] **Step 3: Implement tool config types, entity, repository, service, controller**

Create `ToolType.java`:

```java
package com.agentscope.demo.toolconfig;

public enum ToolType {
    BUILTIN,
    JSON_RPC,
    MCP
}
```

Create `ToolAuthType.java`:

```java
package com.agentscope.demo.toolconfig;

public enum ToolAuthType {
    NONE,
    BEARER,
    BASIC,
    API_KEY
}
```

Create `ToolConfigRequest.java`:

```java
package com.agentscope.demo.toolconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolConfigRequest(
        @NotBlank String name,
        String description,
        @NotNull ToolType toolType,
        String endpoint,
        ToolAuthType authType,
        String authRef,
        String inputSchema,
        String config,
        Integer timeoutMs,
        boolean enabled
) {
}
```

Create `ToolConfigResponse.java`:

```java
package com.agentscope.demo.toolconfig;

import java.util.UUID;

public record ToolConfigResponse(
        UUID id,
        String tenantId,
        String name,
        String description,
        ToolType toolType,
        String endpoint,
        ToolAuthType authType,
        String authRef,
        String inputSchema,
        String config,
        int timeoutMs,
        boolean enabled
) {
}
```

Create `ToolTestResponse.java`:

```java
package com.agentscope.demo.toolconfig;

public record ToolTestResponse(boolean success, String observation) {
}
```

Create `ToolConfigEntity.java` as a JPA entity mapped to `tool_configs`, with fields matching the migration: `id`, `tenantId`, `name`, `description`, `toolType`, `endpoint`, `authType`, `authRef`, `inputSchema`, `config`, `timeoutMs`, `enabled`, `createdAt`, and `updatedAt`. Use `@Enumerated(EnumType.STRING)` on `toolType` and `authType`, and `@Column(columnDefinition = "jsonb")` on `inputSchema` and `config`.

Create `ToolConfigRepository.java`:

```java
package com.agentscope.demo.toolconfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolConfigRepository extends JpaRepository<ToolConfigEntity, UUID> {
    boolean existsByTenantIdAndName(String tenantId, String name);
    List<ToolConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId);
    List<ToolConfigEntity> findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc(String tenantId, ToolType toolType);
    Optional<ToolConfigEntity> findByTenantIdAndId(String tenantId, UUID id);
    void deleteByTenantIdAndId(String tenantId, UUID id);
}
```

Create `ToolConfigService.java` with these public methods:

```java
public ToolConfigResponse create(ToolConfigRequest request)
public List<ToolConfigResponse> list(ToolType type)
public void delete(UUID id)
public ToolTestResponse test(UUID id)
```

`create` must reject duplicate names with `existsByTenantIdAndName`, set `timeoutMs` to `30000` when absent, and default `inputSchema` / `config` to `{}` when absent.

`test(UUID id)` must load by `findByTenantIdAndId(...)` and return:

```java
return new ToolTestResponse(true, "mock observation for " + entity.toolType() + " tool " + entity.name());
```

Create `ToolConfigController.java` with:

```java
@RestController
@RequestMapping("/api/tool-configs")
public class ToolConfigController {
    @GetMapping
    public List<ToolConfigResponse> list(@RequestParam(required = false) ToolType type) {
        return service.list(type);
    }

    @PostMapping
    public ToolConfigResponse create(@Valid @RequestBody ToolConfigRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test")
    public ToolTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }
}
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ToolConfigServiceTest test
```

Expected: PASS.

---

## Task 4: Add Chat Runtime Model And Tool Resolution

**Files:**
- Modify: `backend/src/main/java/com/agentscope/demo/chat/ChatRequest.java`
- Create: `backend/src/main/java/com/agentscope/demo/chat/ChatRuntimeSelection.java`
- Create: `backend/src/main/java/com/agentscope/demo/chat/ChatRuntimeResolver.java`
- Modify: `backend/src/main/java/com/agentscope/demo/chat/HybridChatExecutionService.java`
- Modify: `backend/src/main/java/com/agentscope/demo/chat/ChatStreamEventMapper.java`
- Test: `backend/src/test/java/com/agentscope/demo/chat/ChatRuntimeResolverTest.java`

- [ ] **Step 1: Write failing resolver tests**

Create `ChatRuntimeResolverTest.java`:

```java
package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.model.ReasoningEffort;
import com.agentscope.demo.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRuntimeResolverTest {

    @Test
    void requestModelOverrideWinsOverDefaultTextModel() {
        UUID selectedId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        ModelConfigRepository models = new StubModelRepository(List.of(
                model(selectedId, "tenant-a", "selected", ModelType.REASONING),
                model(defaultId, "tenant-a", "default", ModelType.TEXT)
        ));
        ChatRuntimeResolver resolver = new ChatRuntimeResolver(models);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(null, null, null, "hello", selectedId, ReasoningEffort.HIGH, List.of(), null, null, null));
            assertThat(selection.model().id()).isEqualTo(selectedId);
            assertThat(selection.reasoningEffort()).isEqualTo(ReasoningEffort.HIGH);
            assertThat(selection.warning()).isEmpty();
        });
    }

    @Test
    void reasoningEffortOnTextModelProducesWarning() {
        UUID modelId = UUID.randomUUID();
        ModelConfigRepository models = new StubModelRepository(List.of(model(modelId, "tenant-a", "text", ModelType.TEXT)));
        ChatRuntimeResolver resolver = new ChatRuntimeResolver(models);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(null, null, null, "hello", modelId, ReasoningEffort.HIGH, List.of(), null, null, null));
            assertThat(selection.warning()).contains("ignored");
        });
    }

    private static ModelConfigEntity model(UUID id, String tenantId, String name, ModelType type) {
        Instant now = Instant.now();
        return new ModelConfigEntity(id, tenantId, name, "mock", type, name, null, null, null, null, null, null, "[]", "[]", "{}", true, now, now);
    }
}
```

Add a simple `StubModelRepository` inside the test that implements `findByTenantIdAndId`, `findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc`, and throws `UnsupportedOperationException` for unused methods.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ChatRuntimeResolverTest test
```

Expected: compilation fails because new request fields and resolver do not exist.

- [ ] **Step 3: Extend `ChatRequest`**

Replace `ChatRequest.java` with:

```java
package com.agentscope.demo.chat;

import com.agentscope.demo.model.ReasoningEffort;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
        UUID conversationId,
        UUID agentConfigId,
        UUID userId,
        @NotBlank String prompt,
        UUID modelId,
        ReasoningEffort reasoningEffort,
        List<UUID> toolIds,
        UUID sttModelId,
        UUID ttsModelId,
        UUID audioArtifactId
) {
    public ChatRequest(UUID conversationId, UUID agentConfigId, UUID userId, String prompt) {
        this(conversationId, agentConfigId, userId, prompt, null, null, List.of(), null, null, null);
    }
}
```

- [ ] **Step 4: Add resolver types**

Create `ChatRuntimeSelection.java`:

```java
package com.agentscope.demo.chat;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ReasoningEffort;
import java.util.Optional;

public record ChatRuntimeSelection(
        ModelConfigEntity model,
        ReasoningEffort reasoningEffort,
        Optional<String> warning
) {
}
```

Create `ChatRuntimeResolver.java`:

```java
package com.agentscope.demo.chat;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.tenant.TenantContext;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChatRuntimeResolver {
    private final ModelConfigRepository modelRepository;

    public ChatRuntimeResolver(ModelConfigRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    public ChatRuntimeSelection resolve(ChatRequest request) {
        String tenantId = TenantContext.currentTenantId();
        ModelConfigEntity model = request.modelId() == null
                ? modelRepository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(tenantId, ModelType.TEXT).stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No enabled text model configured"))
                : modelRepository.findByTenantIdAndId(tenantId, request.modelId())
                        .orElseThrow(() -> new IllegalArgumentException("Selected model not found"));
        Optional<String> warning = Optional.empty();
        if (request.reasoningEffort() != null && model.modelType() != ModelType.REASONING) {
            warning = Optional.of("reasoningEffort ignored because selected model is " + model.modelType());
        }
        return new ChatRuntimeSelection(model, request.reasoningEffort(), warning);
    }
}
```

- [ ] **Step 5: Emit model resolution warning in stream**

Modify `HybridChatExecutionService` constructor to accept `ChatRuntimeResolver`. At the start of `execute`, call resolver and add a collapsed `model_resolved` event after the route event:

```java
ChatRuntimeSelection selection = runtimeResolver.resolve(request);
ChatStreamEvent modelResolved = mapper.system(runId, "model_resolved", Map.of(
        "content", "Model: " + selection.model().name(),
        "modelId", selection.model().id().toString()
));
Flux<ChatStreamEvent> prefix = selection.warning()
        .map(warning -> Flux.just(route, modelResolved, mapper.system(runId, "warning", Map.of("content", warning))))
        .orElseGet(() -> Flux.just(route, modelResolved));
```

Use `prefix.concatWith(...)` instead of `Flux.concat(Flux.just(route), ...)`.

- [ ] **Step 6: Run focused tests**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=ChatRuntimeResolverTest,HybridChatExecutionPersistenceTest test
```

Expected: PASS after updating test constructor calls with a stub resolver.

---

## Task 5: Add Agent Model And Tool Binding Persistence

**Files:**
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigEntity.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentToolBindingEntity.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigRepository.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentToolBindingRepository.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigRequest.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigResponse.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigService.java`
- Create: `backend/src/main/java/com/agentscope/demo/agent/AgentConfigController.java`
- Test: `backend/src/test/java/com/agentscope/demo/agent/AgentConfigServiceTest.java`

- [ ] **Step 1: Write failing agent binding test**

Create `AgentConfigServiceTest.java` with a service-level in-memory repository test that creates an agent with `primaryModelId`, `reasoningModelId`, `defaultReasoningEffort`, `toolIds`, and `knowledgeBaseIds`, then asserts the response contains all ids and the tenant id.

Use the exact request shape:

```java
new AgentConfigRequest(
        "Research Agent",
        "Use tools carefully.",
        primaryModelId,
        reasoningModelId,
        null,
        ReasoningEffort.MEDIUM,
        List.of(toolId),
        List.of(knowledgeBaseId)
)
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=AgentConfigServiceTest test
```

Expected: compilation fails because the agent package does not exist.

- [ ] **Step 3: Implement JPA repositories with named methods**

`AgentConfigRepository`:

```java
Optional<AgentConfigEntity> findByTenantIdAndId(String tenantId, UUID id);
List<AgentConfigEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
void deleteByTenantIdAndId(String tenantId, UUID id);
```

`AgentToolBindingRepository`:

```java
List<AgentToolBindingEntity> findByTenantIdAndAgentConfigId(String tenantId, UUID agentConfigId);
void deleteByTenantIdAndAgentConfigId(String tenantId, UUID agentConfigId);
```

- [ ] **Step 4: Implement service/controller**

Expose:

```text
GET /api/agent-configs
POST /api/agent-configs
DELETE /api/agent-configs/{id}
```

`AgentConfigService.create(...)` must save `AgentConfigEntity`, delete existing bindings for the agent id, then save one `AgentToolBindingEntity` per `toolId`.

- [ ] **Step 5: Run focused test**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=AgentConfigServiceTest test
```

Expected: PASS.

---

## Task 6: Add Voice Upload, Mock STT, Mock TTS, And Playback Content API

**Files:**
- Create: `backend/src/main/java/com/agentscope/demo/voice/AudioDirection.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/AudioArtifactEntity.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/AudioArtifactRepository.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/SpeechToTextClient.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/TextToSpeechClient.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/MockSpeechToTextClient.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/MockTextToSpeechClient.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/VoiceService.java`
- Create: `backend/src/main/java/com/agentscope/demo/voice/VoiceController.java`
- Test: `backend/src/test/java/com/agentscope/demo/voice/VoiceServiceTest.java`

- [ ] **Step 1: Write failing voice service test**

Create `VoiceServiceTest.java`:

```java
package com.agentscope.demo.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.storage.FileStorage;
import com.agentscope.demo.tenant.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoiceServiceTest {

    @Test
    void storesRecordingAndReturnsMockTranscript() {
        RecordingAudioArtifactRepository repository = new RecordingAudioArtifactRepository();
        VoiceService service = new VoiceService(repository, new MemoryStorage(), new MockSpeechToTextClient(), new MockTextToSpeechClient());

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceTranscriptionResponse response = service.transcribe("recording.webm", "audio/webm", new byte[]{1, 2, 3}, null);
            assertThat(response.transcript()).contains("mock transcript");
            assertThat(repository.saved.direction()).isEqualTo(AudioDirection.INPUT);
            assertThat(repository.saved.tenantId()).isEqualTo("tenant-a");
        });
    }

    @Test
    void storesSpeechOutputAndReturnsContentUrl() {
        RecordingAudioArtifactRepository repository = new RecordingAudioArtifactRepository();
        VoiceService service = new VoiceService(repository, new MemoryStorage(), new MockSpeechToTextClient(), new MockTextToSpeechClient());

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceSpeechResponse response = service.speech(new VoiceSpeechRequest("hello", null));
            assertThat(response.audioUrl()).contains("/api/audio-artifacts/");
            assertThat(repository.saved.direction()).isEqualTo(AudioDirection.OUTPUT);
        });
    }
}
```

Add in-memory repository/storage helpers inside the test.

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=VoiceServiceTest test
```

Expected: compilation fails because voice types do not exist.

- [ ] **Step 3: Implement voice records, repository, clients, service, controller**

Records to create:

```java
public record VoiceTranscriptionResponse(UUID audioArtifactId, String transcript) {}
public record VoiceSpeechRequest(String text, UUID ttsModelId) {}
public record VoiceSpeechResponse(UUID audioArtifactId, String audioUrl, String mimeType) {}
```

`AudioArtifactRepository` named methods:

```java
Optional<AudioArtifactEntity> findByTenantIdAndId(String tenantId, UUID id);
List<AudioArtifactEntity> findByTenantIdAndConversationIdOrderByCreatedAtDesc(String tenantId, UUID conversationId);
```

`MockSpeechToTextClient.transcribe(...)` returns `"mock transcript from " + filename`.

`MockTextToSpeechClient.synthesize(...)` returns UTF-8 bytes for `"mock audio: " + text` with MIME `audio/plain`.

Controller endpoints:

```text
POST /api/voice/transcriptions
POST /api/voice/speech
GET /api/audio-artifacts/{id}
GET /api/audio-artifacts/{id}/content
```

- [ ] **Step 4: Run focused test**

Run:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn -q -Dtest=VoiceServiceTest test
```

Expected: PASS.

---

## Task 7: Frontend Settings Pages And Chat Composer Controls

**Files:**
- Create: `frontend/src/types/config.ts`
- Create: `frontend/src/lib/api.ts`
- Create: `frontend/src/components/ModelSettings.tsx`
- Create: `frontend/src/components/ToolSettings.tsx`
- Create: `frontend/src/components/AgentSettings.tsx`
- Create: `frontend/src/components/ChatComposer.tsx`
- Create: `frontend/src/components/AudioMessage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/lib/stream.ts`
- Test: `frontend/src/components/ChatComposer.test.tsx`
- Test: `frontend/src/lib/stream.test.ts`

- [ ] **Step 1: Write failing composer test**

Create `frontend/src/components/ChatComposer.test.tsx`:

```tsx
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChatComposer } from "./ChatComposer";
import type { ModelConfig } from "../types/config";

const models: ModelConfig[] = [
  { id: "text-1", name: "Text", modelType: "TEXT", provider: "mock", modelName: "text" },
  { id: "reason-1", name: "Reasoner", modelType: "REASONING", provider: "mock", modelName: "reasoner" }
];

describe("ChatComposer", () => {
  it("sends selected model and reasoning effort", () => {
    const submit = vi.fn();
    render(<ChatComposer models={models} tools={[]} streaming={false} onSubmit={submit} onTranscribe={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Model"), { target: { value: "reason-1" } });
    fireEvent.change(screen.getByLabelText("Reasoning effort"), { target: { value: "HIGH" } });
    fireEvent.change(screen.getByPlaceholderText("Message AgentScope"), { target: { value: "plan this" } });
    fireEvent.click(screen.getByLabelText("Send message"));

    expect(submit).toHaveBeenCalledWith(expect.objectContaining({
      prompt: "plan this",
      modelId: "reason-1",
      reasoningEffort: "HIGH"
    }));
  });
});
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
cd /Users/stevelin/IdeaProjects/agentscope-demo/frontend
npm test -- ChatComposer.test.tsx --run
```

Expected: fails because `ChatComposer` does not exist.

- [ ] **Step 3: Add config types and API helper**

Create `frontend/src/types/config.ts`:

```ts
export type ModelType = "TEXT" | "REASONING" | "EMBEDDING" | "STT" | "TTS" | "MULTIMODAL";
export type ReasoningEffort = "LOW" | "MEDIUM" | "HIGH";
export type ToolType = "BUILTIN" | "JSON_RPC" | "MCP";

export type ModelConfig = {
  id: string;
  name: string;
  provider: string;
  modelType: ModelType;
  modelName: string;
};

export type ToolConfig = {
  id: string;
  name: string;
  toolType: ToolType;
  description?: string;
};

export type ChatSubmit = {
  prompt: string;
  modelId?: string;
  reasoningEffort?: ReasoningEffort;
  toolIds: string[];
};
```

Create `frontend/src/lib/api.ts`:

```ts
import type { ModelConfig, ToolConfig } from "../types/config";

export async function fetchModels(): Promise<ModelConfig[]> {
  const response = await fetch("/api/model-configs");
  return response.ok ? response.json() : [];
}

export async function fetchTools(): Promise<ToolConfig[]> {
  const response = await fetch("/api/tool-configs");
  return response.ok ? response.json() : [];
}
```

- [ ] **Step 4: Add `ChatComposer`**

Create `frontend/src/components/ChatComposer.tsx` implementing:

- Model `<select aria-label="Model">`
- Conditional `<select aria-label="Reasoning effort">` only when selected model type is `REASONING`
- Prompt textarea with placeholder `Message AgentScope`
- Send button with aria-label `Send message`
- Tool checkbox list if tools exist
- Record button scaffold with aria-label `Record audio`

The submit handler must call `onSubmit({ prompt, modelId, reasoningEffort, toolIds })`.

- [ ] **Step 5: Update App**

Modify `frontend/src/App.tsx`:

- Load models/tools with `fetchModels` and `fetchTools`.
- Replace inline `<form>` with `ChatComposer`.
- POST body should include selected fields:

```ts
body: JSON.stringify(payload)
```

- Add sidebar buttons for `Chat`, `Models`, `Tools`, `Agents`, `Knowledge`.
- Render simple settings page components for Models/Tools/Agents.

- [ ] **Step 6: Update stream labels**

Add labels in `frontend/src/lib/stream.ts`:

```ts
model_resolved: "模型选择",
warning: "提示",
voice_transcription: "语音转写",
audio_output: "语音输出"
```

- [ ] **Step 7: Run frontend tests and build**

Run:

```bash
npm test -- --run
npm run build
```

Expected: tests pass and build succeeds. Vite chunk-size warning is acceptable.

---

## Task 8: Docker-Backed End-To-End Smoke

**Files:**
- No source files unless smoke reveals defects.

- [ ] **Step 1: Start infrastructure**

Run:

```bash
cd /Users/stevelin/IdeaProjects/agentscope-demo
docker compose up -d
docker compose ps
```

Expected: Postgres, Redis, and MinIO are running. Postgres is healthy.

- [ ] **Step 2: Run backend**

Run:

```bash
cd /Users/stevelin/IdeaProjects/agentscope-demo/backend
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn spring-boot:run
```

Expected: backend starts on `http://localhost:8080`.

- [ ] **Step 3: Create model config**

Run from a second shell:

```bash
TENANT="smoke-$(date +%s)"
MODEL_RESPONSE=$(curl -sS -X POST http://localhost:8080/api/model-configs \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: $TENANT" \
  -d '{"name":"DeepSeek Chat","provider":"deepseek","modelType":"TEXT","modelName":"deepseek-chat","baseUrl":"https://api.deepseek.com","apiKeyRef":"DEEPSEEK_APIKEY","inputModalities":["text"],"outputModalities":["text"],"extraParams":"{}","enabled":true}')
MODEL_ID=$(printf '%s' "$MODEL_RESPONSE" | sed -E 's/.*"id":"([^"]+)".*/\1/')
printf '%s\n' "$MODEL_RESPONSE"
```

Expected: JSON response contains `id`, `tenantId`, and `modelType:"TEXT"`.

- [ ] **Step 4: Create tool config**

Run:

```bash
curl -sS -X POST http://localhost:8080/api/tool-configs \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: $TENANT" \
  -d '{"name":"Mock MCP","description":"Smoke MCP","toolType":"MCP","endpoint":"stdio://mock","authType":"NONE","inputSchema":"{}","config":"{}","timeoutMs":30000,"enabled":true}'
```

Expected: JSON response contains `toolType:"MCP"`.

- [ ] **Step 5: Send chat with model override**

Run:

```bash
curl -sS -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: $TENANT" \
  -d "{\"prompt\":\"hello with selected model\",\"modelId\":\"$MODEL_ID\"}" | head -n 30
```

Expected: SSE includes `event:model_resolved` and `event:final_answer`.

- [ ] **Step 6: Smoke voice transcription and speech**

Run:

```bash
TMP_AUDIO=$(mktemp /tmp/agentscope-audio.XXXXXX.webm)
printf 'fake audio' > "$TMP_AUDIO"
curl -sS -X POST http://localhost:8080/api/voice/transcriptions \
  -H "X-Tenant-Id: $TENANT" \
  -F "file=@$TMP_AUDIO;type=audio/webm"
curl -sS -X POST http://localhost:8080/api/voice/speech \
  -H 'Content-Type: application/json' \
  -H "X-Tenant-Id: $TENANT" \
  -d '{"text":"hello audio"}'
rm -f "$TMP_AUDIO"
```

Expected: transcription response contains `mock transcript`; speech response contains `audioUrl`.

- [ ] **Step 7: Run full verification**

Run:

```bash
cd /Users/stevelin/IdeaProjects/agentscope-demo/backend
source "$HOME/.sdkman/bin/sdkman-init.sh" && mvn test
cd /Users/stevelin/IdeaProjects/agentscope-demo/frontend
npm test -- --run
npm run build
```

Expected: all backend tests pass, all frontend tests pass, frontend build succeeds.

---

## Self-Review Notes

- Spec coverage: model configs are covered in Tasks 1-2; tool configs in Task 3; chat model/tool selection in Task 4; agent bindings in Task 5; voice upload/playback in Task 6; frontend model/tool/voice controls in Task 7; smoke verification in Task 8.
- JPA constraint: normal CRUD repositories use named Spring Data methods. pgvector custom behavior is not expanded here and remains isolated in existing RAG code until a separate RAG search plan.
- Scope B: text/embedding adapter seams are established via model configs and runtime resolver; voice/JsonRPC/MCP are mock/extensible in this phase.
- No git commits are included because `/Users/stevelin/IdeaProjects/agentscope-demo` is not currently a git repository.
