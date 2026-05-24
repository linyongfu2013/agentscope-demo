package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.agentscope.demo.agent.AgentConfigEntity;
import com.agentscope.demo.agent.AgentConfigRepository;
import com.agentscope.demo.agent.AgentToolBindingEntity;
import com.agentscope.demo.agent.AgentToolBindingRepository;
import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.model.ReasoningEffort;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.toolconfig.ToolConfigEntity;
import com.agentscope.demo.toolconfig.ToolConfigRepository;
import com.agentscope.demo.toolconfig.ToolType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatRuntimeResolverTest {

    @Test
    void requestModelOverrideWinsOverDefaultTextModel() {
        UUID selectedId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        when(repository.findByTenantIdAndId("tenant-a", selectedId))
                .thenReturn(Optional.of(model(selectedId, "tenant-a", "selected", ModelType.REASONING)));
        when(repository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ModelType.TEXT))
                .thenReturn(List.of(model(defaultId, "tenant-a", "default", ModelType.TEXT)));
        ChatRuntimeResolver resolver = resolver(repository);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(
                    null, null, null, "hello", selectedId, ReasoningEffort.HIGH, List.of(), null, null, null
            ));

            assertThat(selection.model().getId()).isEqualTo(selectedId);
            assertThat(selection.reasoningEffort()).isEqualTo(ReasoningEffort.HIGH);
            assertThat(selection.warning()).isEmpty();
        });
    }

    @Test
    void reasoningEffortOnTextModelProducesWarning() {
        UUID modelId = UUID.randomUUID();
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        when(repository.findByTenantIdAndId("tenant-a", modelId))
                .thenReturn(Optional.of(model(modelId, "tenant-a", "text", ModelType.TEXT)));
        ChatRuntimeResolver resolver = resolver(repository);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(
                    null, null, null, "hello", modelId, ReasoningEffort.HIGH, List.of(), null, null, null
            ));

            assertThat(selection.reasoningEffort()).isNull();
            assertThat(selection.warning()).isPresent();
            assertThat(selection.warning().orElseThrow()).contains("ignored");
        });
    }

    @Test
    void noConfiguredTextModelReturnsMockChatFallback() {
        ModelConfigRepository repository = Mockito.mock(ModelConfigRepository.class);
        when(repository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc("tenant-a", ModelType.TEXT))
                .thenReturn(List.of());
        ChatRuntimeResolver resolver = resolver(repository);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(null, null, null, "hello"));

            assertThat(selection.model().getName()).isEqualTo("Mock Chat");
            assertThat(selection.model().getProvider()).isEqualTo("mock");
            assertThat(selection.model().getModelType()).isEqualTo(ModelType.TEXT);
            assertThat(selection.model().getModelName()).isEqualTo("mock-chat");
            assertThat(selection.model().isEnabled()).isTrue();
            assertThat(selection.agentConfig().modelName()).isEqualTo("mock-chat");
        });
    }

    @Test
    void selectedAgentIsTenantScopedAndProvidesRuntimeConfig() {
        UUID agentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID toolId = UUID.randomUUID();
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        AgentToolBindingRepository bindingRepository = Mockito.mock(AgentToolBindingRepository.class);
        ToolConfigRepository toolRepository = Mockito.mock(ToolConfigRepository.class);
        when(agentRepository.findByTenantIdAndId("tenant-a", agentId)).thenReturn(Optional.of(agent(agentId, modelId)));
        when(modelRepository.findByTenantIdAndId("tenant-a", modelId))
                .thenReturn(Optional.of(model(modelId, "tenant-a", "agent-model", ModelType.TEXT)));
        when(bindingRepository.findByTenantIdAndAgentConfigId("tenant-a", agentId))
                .thenReturn(List.of(new AgentToolBindingEntity(agentId, toolId, "tenant-a")));
        when(toolRepository.findByTenantIdAndId("tenant-a", toolId)).thenReturn(Optional.of(tool(toolId)));
        ChatRuntimeResolver resolver = new ChatRuntimeResolver(modelRepository, agentRepository, bindingRepository, toolRepository);

        TenantContext.runWithTenant("tenant-a", () -> {
            ChatRuntimeSelection selection = resolver.resolve(new ChatRequest(
                    null, agentId, null, "research this", null, null, List.of(), null, null, null
            ));

            assertThat(selection.model().getId()).isEqualTo(modelId);
            assertThat(selection.agentConfig().id()).isEqualTo(agentId);
            assertThat(selection.agentConfig().name()).isEqualTo("Research Agent");
            assertThat(selection.agentConfig().systemPrompt()).isEqualTo("Research carefully.");
            assertThat(selection.agentConfig().tools()).containsExactly("mcp");
        });
    }

    @Test
    void rejectsAgentFromAnotherTenant() {
        UUID agentId = UUID.randomUUID();
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        AgentConfigRepository agentRepository = Mockito.mock(AgentConfigRepository.class);
        when(agentRepository.findByTenantIdAndId("tenant-a", agentId)).thenReturn(Optional.empty());
        ChatRuntimeResolver resolver = new ChatRuntimeResolver(
                modelRepository,
                agentRepository,
                Mockito.mock(AgentToolBindingRepository.class),
                Mockito.mock(ToolConfigRepository.class)
        );

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> resolver.resolve(new ChatRequest(
                        null, agentId, null, "hello", null, null, List.of(), null, null, null
                )))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Selected agent not found")
        );
    }

    private static ChatRuntimeResolver resolver(ModelConfigRepository repository) {
        return new ChatRuntimeResolver(
                repository,
                Mockito.mock(AgentConfigRepository.class),
                Mockito.mock(AgentToolBindingRepository.class),
                Mockito.mock(ToolConfigRepository.class)
        );
    }

    private static ModelConfigEntity model(UUID id, String tenantId, String name, ModelType modelType) {
        Instant now = Instant.now();
        ModelConfigEntity model = new ModelConfigEntity();
        model.setId(id);
        model.setTenantId(tenantId);
        model.setName(name);
        model.setProvider("mock");
        model.setModelType(modelType);
        model.setModelName(name);
        model.setEnabled(true);
        model.setInputModalities("[]");
        model.setOutputModalities("[]");
        model.setExtraParams("{}");
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return model;
    }

    private static AgentConfigEntity agent(UUID id, UUID modelId) {
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setId(id);
        entity.setTenantId("tenant-a");
        entity.setName("Research Agent");
        entity.setSystemPrompt("Research carefully.");
        entity.setPrimaryModelId(modelId);
        entity.setKnowledgeBaseIds(new UUID[0]);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private static ToolConfigEntity tool(UUID id) {
        ToolConfigEntity entity = new ToolConfigEntity();
        entity.setId(id);
        entity.setTenantId("tenant-a");
        entity.setName("MCP tool");
        entity.setToolType(ToolType.MCP);
        entity.setEnabled(true);
        return entity;
    }
}
