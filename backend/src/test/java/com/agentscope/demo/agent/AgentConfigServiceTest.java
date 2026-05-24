package com.agentscope.demo.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentscope.demo.model.ReasoningEffort;
import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.rag.KnowledgeRepository;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import com.agentscope.demo.toolconfig.ToolConfigEntity;
import com.agentscope.demo.toolconfig.ToolConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

class AgentConfigServiceTest {

    @Test
    void createReturnsModelToolKnowledgeIdsAndTenant() {
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        ToolConfigRepository toolRepository = Mockito.mock(ToolConfigRepository.class);
        KnowledgeRepository knowledgeRepository = Mockito.mock(KnowledgeRepository.class);
        AgentConfigService service = new AgentConfigService(
                agentRepository,
                bindingRepository,
                tenantRepository,
                modelRepository,
                toolRepository,
                knowledgeRepository
        );
        UUID agentId = UUID.randomUUID();
        UUID primaryModelId = UUID.randomUUID();
        UUID reasoningModelId = UUID.randomUUID();
        UUID embeddingModelId = UUID.randomUUID();
        UUID toolId = UUID.randomUUID();
        UUID secondToolId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        when(modelRepository.findByTenantIdAndId("tenant-a", primaryModelId)).thenReturn(Optional.of(new ModelConfigEntity()));
        when(modelRepository.findByTenantIdAndId("tenant-a", reasoningModelId)).thenReturn(Optional.of(new ModelConfigEntity()));
        when(modelRepository.findByTenantIdAndId("tenant-a", embeddingModelId)).thenReturn(Optional.of(new ModelConfigEntity()));
        when(toolRepository.findByTenantIdAndId("tenant-a", toolId)).thenReturn(Optional.of(new ToolConfigEntity()));
        when(toolRepository.findByTenantIdAndId("tenant-a", secondToolId)).thenReturn(Optional.of(new ToolConfigEntity()));
        when(knowledgeRepository.existsKnowledgeBase("tenant-a", knowledgeBaseId)).thenReturn(true);
        when(agentRepository.save(any(AgentConfigEntity.class))).thenAnswer(invocation -> {
            AgentConfigEntity entity = invocation.getArgument(0);
            entity.setId(agentId);
            entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            entity.setUpdatedAt(Instant.parse("2026-05-24T10:00:00Z"));
            return entity;
        });

        TenantContext.runWithTenant("tenant-a", () -> {
            AgentConfigResponse response = service.create(new AgentConfigRequest(
                    "Research Agent",
                    "Use tools carefully.",
                    primaryModelId,
                    reasoningModelId,
                    embeddingModelId,
                    ReasoningEffort.MEDIUM,
                    List.of(toolId, secondToolId),
                    List.of(knowledgeBaseId)
            ));

            assertThat(response.id()).isEqualTo(agentId);
            assertThat(response.tenantId()).isEqualTo("tenant-a");
            assertThat(response.name()).isEqualTo("Research Agent");
            assertThat(response.systemPrompt()).isEqualTo("Use tools carefully.");
            assertThat(response.primaryModelId()).isEqualTo(primaryModelId);
            assertThat(response.reasoningModelId()).isEqualTo(reasoningModelId);
            assertThat(response.embeddingModelId()).isEqualTo(embeddingModelId);
            assertThat(response.defaultReasoningEffort()).isEqualTo(ReasoningEffort.MEDIUM);
            assertThat(response.toolIds()).containsExactly(toolId, secondToolId);
            assertThat(response.knowledgeBaseIds()).containsExactly(knowledgeBaseId);
        });

        verify(tenantRepository).ensureExists("tenant-a");
        verify(modelRepository).findByTenantIdAndId("tenant-a", primaryModelId);
        verify(modelRepository).findByTenantIdAndId("tenant-a", reasoningModelId);
        verify(modelRepository).findByTenantIdAndId("tenant-a", embeddingModelId);
        verify(toolRepository).findByTenantIdAndId("tenant-a", toolId);
        verify(toolRepository).findByTenantIdAndId("tenant-a", secondToolId);
        verify(knowledgeRepository).existsKnowledgeBase("tenant-a", knowledgeBaseId);
        verify(bindingRepository).deleteByTenantIdAndAgentConfigId("tenant-a", agentId);
        ArgumentCaptor<AgentToolBindingEntity> bindingCaptor = ArgumentCaptor.forClass(AgentToolBindingEntity.class);
        verify(bindingRepository, Mockito.times(2)).save(bindingCaptor.capture());
        assertThat(bindingCaptor.getAllValues())
                .extracting(AgentToolBindingEntity::getAgentConfigId)
                .containsOnly(agentId);
        assertThat(bindingCaptor.getAllValues())
                .extracting(AgentToolBindingEntity::getTenantId)
                .containsOnly("tenant-a");
        assertThat(bindingCaptor.getAllValues())
                .extracting(AgentToolBindingEntity::getToolConfigId)
                .containsExactly(toolId, secondToolId);
    }

    @Test
    void deleteRemovesBindingsBeforeTenantScopedAgentDelete() {
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        AgentConfigService service = new AgentConfigService(
                agentRepository,
                bindingRepository,
                tenantRepository,
                Mockito.mock(ModelConfigRepository.class),
                Mockito.mock(ToolConfigRepository.class),
                Mockito.mock(KnowledgeRepository.class)
        );
        UUID agentId = UUID.randomUUID();

        TenantContext.runWithTenant("tenant-a", () -> service.delete(agentId));

        InOrder inOrder = Mockito.inOrder(bindingRepository, agentRepository);
        inOrder.verify(bindingRepository).deleteByTenantIdAndAgentConfigId("tenant-a", agentId);
        inOrder.verify(agentRepository).deleteByTenantIdAndId("tenant-a", agentId);
    }

    @Test
    void createRejectsModelThatOnlyExistsForAnotherTenant() {
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        AgentConfigService service = new AgentConfigService(
                agentRepository,
                bindingRepository,
                tenantRepository,
                modelRepository,
                Mockito.mock(ToolConfigRepository.class),
                Mockito.mock(KnowledgeRepository.class)
        );
        UUID primaryModelId = UUID.randomUUID();
        when(modelRepository.findByTenantIdAndId("tenant-a", primaryModelId)).thenReturn(Optional.empty());
        when(modelRepository.findByTenantIdAndId("tenant-b", primaryModelId)).thenReturn(Optional.of(new ModelConfigEntity()));

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(request(primaryModelId, null, null, List.of(), List.of())))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Model config not found for tenant")
        );

        verify(modelRepository).findByTenantIdAndId("tenant-a", primaryModelId);
        verify(modelRepository, never()).findByTenantIdAndId("tenant-b", primaryModelId);
        verify(agentRepository, never()).save(any(AgentConfigEntity.class));
    }

    @Test
    void createRejectsToolThatOnlyExistsForAnotherTenant() {
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ToolConfigRepository toolRepository = Mockito.mock(ToolConfigRepository.class);
        AgentConfigService service = new AgentConfigService(
                agentRepository,
                bindingRepository,
                tenantRepository,
                Mockito.mock(ModelConfigRepository.class),
                toolRepository,
                Mockito.mock(KnowledgeRepository.class)
        );
        UUID toolId = UUID.randomUUID();
        when(toolRepository.findByTenantIdAndId("tenant-a", toolId)).thenReturn(Optional.empty());
        when(toolRepository.findByTenantIdAndId("tenant-b", toolId)).thenReturn(Optional.of(new ToolConfigEntity()));

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(request(null, null, null, List.of(toolId), List.of())))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Tool config not found for tenant")
        );

        verify(toolRepository).findByTenantIdAndId("tenant-a", toolId);
        verify(toolRepository, never()).findByTenantIdAndId("tenant-b", toolId);
        verify(agentRepository, never()).save(any(AgentConfigEntity.class));
    }

    @Test
    void createRejectsKnowledgeBaseThatOnlyExistsForAnotherTenant() {
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        KnowledgeRepository knowledgeRepository = Mockito.mock(KnowledgeRepository.class);
        AgentConfigService service = new AgentConfigService(
                agentRepository,
                bindingRepository,
                tenantRepository,
                Mockito.mock(ModelConfigRepository.class),
                Mockito.mock(ToolConfigRepository.class),
                knowledgeRepository
        );
        UUID knowledgeBaseId = UUID.randomUUID();
        when(knowledgeRepository.existsKnowledgeBase("tenant-a", knowledgeBaseId)).thenReturn(false);
        when(knowledgeRepository.existsKnowledgeBase("tenant-b", knowledgeBaseId)).thenReturn(true);

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.create(request(null, null, null, List.of(), List.of(knowledgeBaseId))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Knowledge base not found for tenant")
        );

        verify(knowledgeRepository).existsKnowledgeBase("tenant-a", knowledgeBaseId);
        verify(knowledgeRepository, never()).existsKnowledgeBase("tenant-b", knowledgeBaseId);
        verify(agentRepository, never()).save(any(AgentConfigEntity.class));
    }

    private static AgentConfigRequest request(
            UUID primaryModelId,
            UUID reasoningModelId,
            UUID embeddingModelId,
            List<UUID> toolIds,
            List<UUID> knowledgeBaseIds
    ) {
        return new AgentConfigRequest(
                "Research Agent",
                "Use tools carefully.",
                primaryModelId,
                reasoningModelId,
                embeddingModelId,
                ReasoningEffort.MEDIUM,
                toolIds,
                knowledgeBaseIds
        );
    }
}
