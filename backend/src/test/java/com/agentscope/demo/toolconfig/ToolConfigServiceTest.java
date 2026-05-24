package com.agentscope.demo.toolconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ToolConfigServiceTest {

    @Test
    void createsAndListsEnabledToolsByTenantAndType() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID savedId = UUID.randomUUID();

        when(repository.existsByTenantIdAndName("tenant-a", "Knowledge MCP")).thenReturn(false);
        when(repository.save(any(ToolConfigEntity.class))).thenAnswer(invocation -> {
            ToolConfigEntity entity = invocation.getArgument(0);
            entity.setId(savedId);
            entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            entity.setUpdatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            return entity;
        });
        when(repository.findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ToolType.MCP))
                .thenReturn(List.of(entity(savedId, "tenant-a", "Knowledge MCP", ToolType.MCP)));

        TenantContext.runWithTenant("tenant-a", () -> {
            ToolConfigResponse created = service.create(new ToolConfigRequest(
                    "Knowledge MCP",
                    "tenant knowledge tools",
                    ToolType.MCP,
                    "http://localhost:9000/mcp",
                    null,
                    "secret/mcp",
                    null,
                    null,
                    null,
                    true
            ));

            assertThat(created.id()).isEqualTo(savedId);
            assertThat(created.tenantId()).isEqualTo("tenant-a");
            assertThat(created.authType()).isEqualTo(ToolAuthType.NONE);
            assertThat(created.inputSchema()).isEqualTo("{}");
            assertThat(created.config()).isEqualTo("{}");
            assertThat(created.timeoutMs()).isEqualTo(30000);

            List<ToolConfigResponse> listed = service.list(ToolType.MCP);

            assertThat(listed).hasSize(1);
            assertThat(listed.getFirst().tenantId()).isEqualTo("tenant-a");
            assertThat(listed.getFirst().toolType()).isEqualTo(ToolType.MCP);
        });

        ArgumentCaptor<ToolConfigEntity> saved = ArgumentCaptor.forClass(ToolConfigEntity.class);
        verify(tenantRepository).ensureExists("tenant-a");
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getValue().getAuthType()).isEqualTo(ToolAuthType.NONE);
        assertThat(saved.getValue().getTimeoutMs()).isEqualTo(30000);
        verify(repository).findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ToolType.MCP);
    }

    @Test
    void listsAllEnabledToolsWhenTypeIsAbsent() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        when(repository.findByTenantIdAndEnabledTrueOrderByCreatedAtDesc("tenant-a"))
                .thenReturn(List.of(entity(id, "tenant-a", "Built In", ToolType.BUILTIN)));

        TenantContext.runWithTenant("tenant-a", () -> {
            List<ToolConfigResponse> listed = service.list(null);

            assertThat(listed).hasSize(1);
            assertThat(listed.getFirst().name()).isEqualTo("Built In");
            assertThat(listed.getFirst().toolType()).isEqualTo(ToolType.BUILTIN);
        });

        verify(repository).findByTenantIdAndEnabledTrueOrderByCreatedAtDesc("tenant-a");
    }

    @Test
    void rejectsDuplicateNamesWithinTenant() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);

        when(repository.existsByTenantIdAndName("tenant-a", "Duplicate")).thenReturn(true);

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(new ToolConfigRequest(
                        "Duplicate",
                        null,
                        ToolType.JSON_RPC,
                        "https://example.com/rpc",
                        ToolAuthType.BEARER,
                        "secret/token",
                        "{}",
                        "{}",
                        5000,
                        true
                ))).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("already exists")
        );

        verify(tenantRepository).ensureExists("tenant-a");
        verify(repository, never()).save(any(ToolConfigEntity.class));
    }

    @Test
    void updatesTenantScopedToolWithoutChangingIdentityFields() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-24T10:00:00Z");
        ToolConfigEntity existing = entity(id, "tenant-a", "Old Tool", ToolType.MCP);
        existing.setCreatedAt(createdAt);

        when(repository.findByTenantIdAndId("tenant-a", id)).thenReturn(Optional.of(existing));
        when(repository.save(any(ToolConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantContext.runWithTenant("tenant-a", () -> {
            ToolConfigResponse updated = service.update(id, new ToolConfigRequest(
                    "Updated Tool",
                    "updated description",
                    ToolType.JSON_RPC,
                    "https://example.com/rpc",
                    ToolAuthType.BEARER,
                    "secret/updated",
                    "{\"type\":\"object\"}",
                    "{\"retries\":2}",
                    7000,
                    false
            ));

            assertThat(updated.id()).isEqualTo(id);
            assertThat(updated.tenantId()).isEqualTo("tenant-a");
            assertThat(updated.createdAt()).isEqualTo(createdAt);
            assertThat(updated.name()).isEqualTo("Updated Tool");
            assertThat(updated.description()).isEqualTo("updated description");
            assertThat(updated.toolType()).isEqualTo(ToolType.JSON_RPC);
            assertThat(updated.endpoint()).isEqualTo("https://example.com/rpc");
            assertThat(updated.authType()).isEqualTo(ToolAuthType.BEARER);
            assertThat(updated.authRef()).isEqualTo("secret/updated");
            assertThat(updated.inputSchema()).isEqualTo("{\"type\":\"object\"}");
            assertThat(updated.config()).isEqualTo("{\"retries\":2}");
            assertThat(updated.timeoutMs()).isEqualTo(7000);
            assertThat(updated.enabled()).isFalse();
        });

        verify(repository).findByTenantIdAndId("tenant-a", id);
        verify(repository).save(existing);
    }

    @Test
    void doesNotUpdateWhenTenantScopedToolIsMissing() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        when(repository.findByTenantIdAndId("tenant-a", id)).thenReturn(Optional.empty());

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.update(id, new ToolConfigRequest(
                        "Missing",
                        null,
                        ToolType.JSON_RPC,
                        "https://example.com/rpc",
                        ToolAuthType.BEARER,
                        "secret/token",
                        "{}",
                        "{}",
                        5000,
                        true
                ))).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("not found")
        );

        verify(repository).findByTenantIdAndId("tenant-a", id);
        verify(repository, never()).save(any(ToolConfigEntity.class));
    }

    @Test
    void testReturnsMockObservationForTenantScopedTool() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        when(repository.findByTenantIdAndId("tenant-a", id))
                .thenReturn(Optional.of(entity(id, "tenant-a", "Search", ToolType.JSON_RPC)));

        TenantContext.runWithTenant("tenant-a", () -> {
            ToolTestResponse response = service.test(id);

            assertThat(response.success()).isTrue();
            assertThat(response.observation()).isEqualTo("mock observation for JSON_RPC tool Search");
        });

        verify(repository).findByTenantIdAndId("tenant-a", id);
    }

    @Test
    void deletesUsingTenantScopedRepositoryMethod() {
        ToolConfigRepository repository = Mockito.mock(ToolConfigRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigService service = new ToolConfigService(repository, tenantRepository);
        UUID id = UUID.randomUUID();

        TenantContext.runWithTenant("tenant-a", () -> service.delete(id));

        verify(repository).deleteByTenantIdAndId("tenant-a", id);
    }

    private static ToolConfigEntity entity(UUID id, String tenantId, String name, ToolType toolType) {
        ToolConfigEntity entity = new ToolConfigEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName(name);
        entity.setDescription("description");
        entity.setToolType(toolType);
        entity.setEndpoint("https://example.com/tool");
        entity.setAuthType(ToolAuthType.NONE);
        entity.setAuthRef("secret/tool");
        entity.setInputSchema("{}");
        entity.setConfig("{}");
        entity.setTimeoutMs(30000);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        return entity;
    }
}
