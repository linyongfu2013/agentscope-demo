package com.agentscope.demo.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ModelConfigServiceTest {

    @Test
    void createsAndListsEnabledModelsByTenantAndType() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigService service = new ModelConfigService(repository, tenantRepository);
        UUID savedId = UUID.randomUUID();

        when(repository.existsByTenantIdAndName("tenant-a", "DeepSeek Chat")).thenReturn(false);
        when(repository.save(any(ModelConfigEntity.class))).thenAnswer(invocation -> {
            ModelConfigEntity entity = invocation.getArgument(0);
            entity.setId(savedId);
            entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            entity.setUpdatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            return entity;
        });
        when(repository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ModelType.TEXT))
                .thenReturn(List.of(entity(savedId, "tenant-a", "DeepSeek Chat", ModelType.TEXT)));

        TenantContext.runWithTenant("tenant-a", () -> {
            ModelConfigResponse created = service.create(new ModelConfigRequest(
                    "DeepSeek Chat",
                    "deepseek",
                    ModelType.TEXT,
                    "deepseek-chat",
                    "https://api.deepseek.com",
                    "secret/deepseek",
                    new BigDecimal("0.700"),
                    4096,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true
            ));

            assertThat(created.id()).isEqualTo(savedId);
            assertThat(created.tenantId()).isEqualTo("tenant-a");
            assertThat(created.inputModalities()).isEmpty();
            assertThat(created.outputModalities()).isEmpty();
            assertThat(created.extraParams()).isEqualTo("{}");

            List<ModelConfigResponse> listed = service.list(ModelType.TEXT);

            assertThat(listed).hasSize(1);
            assertThat(listed.getFirst().tenantId()).isEqualTo("tenant-a");
            assertThat(listed.getFirst().modelType()).isEqualTo(ModelType.TEXT);
        });

        verify(tenantRepository).ensureExists("tenant-a");
        verify(repository).existsByTenantIdAndName("tenant-a", "DeepSeek Chat");
        verify(repository).findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ModelType.TEXT);
    }

    @Test
    void rejectsDuplicateNameWithinTenant() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigService service = new ModelConfigService(repository, tenantRepository);

        when(repository.existsByTenantIdAndName("tenant-a", "Duplicate")).thenReturn(true);

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(new ModelConfigRequest(
                        "Duplicate",
                        "openai",
                        ModelType.TEXT,
                        "gpt-4.1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "{}",
                        true
                ))).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("already exists")
        );

        verify(repository, never()).save(any(ModelConfigEntity.class));
    }

    @Test
    void updatesTenantScopedModelWithoutChangingIdentityFields() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigService service = new ModelConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-24T10:00:00Z");
        ModelConfigEntity existing = entity(id, "tenant-a", "Old Model", ModelType.TEXT);
        existing.setCreatedAt(createdAt);

        when(repository.findByTenantIdAndId("tenant-a", id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ModelConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantContext.runWithTenant("tenant-a", () -> {
            ModelConfigResponse updated = service.update(id, new ModelConfigRequest(
                    "Updated Model",
                    "openai",
                    ModelType.EMBEDDING,
                    "text-embedding-3-large",
                    "https://api.openai.com/v1",
                    "secret/openai",
                    new BigDecimal("0.200"),
                    2048,
                    ReasoningEffort.LOW,
                    3072,
                    List.of("text"),
                    List.of("embedding"),
                    "{\"ranker\":\"v1\"}",
                    false
            ));

            assertThat(updated.id()).isEqualTo(id);
            assertThat(updated.tenantId()).isEqualTo("tenant-a");
            assertThat(updated.createdAt()).isEqualTo(createdAt);
            assertThat(updated.name()).isEqualTo("Updated Model");
            assertThat(updated.provider()).isEqualTo("openai");
            assertThat(updated.modelType()).isEqualTo(ModelType.EMBEDDING);
            assertThat(updated.modelName()).isEqualTo("text-embedding-3-large");
            assertThat(updated.inputModalities()).containsExactly("text");
            assertThat(updated.outputModalities()).containsExactly("embedding");
            assertThat(updated.extraParams()).isEqualTo("{\"ranker\":\"v1\"}");
            assertThat(updated.enabled()).isFalse();
        });

        verify(repository).findByTenantIdAndId("tenant-a", id);
        verify(repository).save(existing);
    }

    @Test
    void doesNotUpdateWhenTenantScopedModelIsMissing() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigService service = new ModelConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        when(repository.findByTenantIdAndId("tenant-a", id)).thenReturn(Optional.empty());

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.update(id, new ModelConfigRequest(
                        "Missing",
                        "openai",
                        ModelType.TEXT,
                        "gpt-4.1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "{}",
                        true
                ))).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not found")
        );

        verify(repository).findByTenantIdAndId("tenant-a", id);
        verify(repository, never()).save(any(ModelConfigEntity.class));
    }

    @Test
    void deletesUsingTenantScopedDeleteMethod() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigService service = new ModelConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        TenantContext.runWithTenant("tenant-a", () -> service.delete(id));

        verify(repository).deleteByTenantIdAndId("tenant-a", id);
    }

    private static ModelConfigEntity entity(UUID id, String tenantId, String name, ModelType modelType) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setProvider("deepseek");
        entity.setModelType(modelType);
        entity.setModelName("deepseek-chat");
        entity.setInputModalities("[]");
        entity.setOutputModalities("[]");
        entity.setExtraParams("{}");
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        return entity;
    }
}
