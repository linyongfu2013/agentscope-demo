package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.secret.MapSecretResolver;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.toolconfig.ToolRuntimeRegistrar;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HybridChatExecutionPersistenceTest {

    @Test
    void recordsTaskRunAndEachStreamEventWithTenant() {
        RecordingTaskRunRepository taskRuns = new RecordingTaskRunRepository();
        RecordingTaskEventRepository taskEvents = new RecordingTaskEventRepository();
        HybridChatExecutionService service = new HybridChatExecutionService(
                new FixedAgentRuntimeFactory(AgentScopeStreamEvent.finalAnswer("provider answer")),
                new ChatStreamEventMapper(),
                runtimeResolver(),
                taskRuns,
                taskEvents
        );

        TenantContext.runWithTenant("tenant-a", () ->
                service.execute(new ChatRequest(null, null, null, "hello")).collectList().block()
        );

        assertThat(taskRuns.createdTenantId).isEqualTo("tenant-a");
        assertThat(taskRuns.createdIntent).isEqualTo(Intent.SELF_DIRECTED);
        assertThat(taskRuns.completed).isTrue();
        assertThat(taskEvents.events).hasSize(3);
        assertThat(taskEvents.events).allSatisfy(event -> assertThat(event.tenantId).isEqualTo("tenant-a"));
        assertThat(taskEvents.events.getFirst().event.type()).isEqualTo("execution_strategy");
        assertThat(taskEvents.events.getFirst().event.payload().get("strategy")).isEqualTo("model_self_directed");
        assertThat(taskEvents.events.get(1).event.type()).isEqualTo("model_resolved");
        assertThat(taskEvents.events.getLast().event.type()).isEqualTo("final_answer");
    }

    @Test
    void simpleIntentUsesConfiguredRuntimeInsteadOfLocalEcho() {
        RecordingTaskRunRepository taskRuns = new RecordingTaskRunRepository();
        RecordingTaskEventRepository taskEvents = new RecordingTaskEventRepository();
        HybridChatExecutionService service = new HybridChatExecutionService(
                new FixedAgentRuntimeFactory(AgentScopeStreamEvent.finalAnswer("provider answer")),
                new ChatStreamEventMapper(),
                runtimeResolver(),
                taskRuns,
                taskEvents
        );

        AtomicReference<List<ChatStreamEvent>> eventsRef = new AtomicReference<>();
        TenantContext.runWithTenant("tenant-a", () ->
                eventsRef.set(service.execute(new ChatRequest(null, null, null, "hello")).collectList().block())
        );
        List<ChatStreamEvent> events = eventsRef.get();

        assertThat(events.getLast().payload().get("content")).isEqualTo("provider answer");
    }

    @Test
    void invalidRuntimeSelectionDoesNotCreateTaskRun() {
        RecordingTaskRunRepository taskRuns = new RecordingTaskRunRepository();
        RecordingTaskEventRepository taskEvents = new RecordingTaskEventRepository();
        ChatRuntimeResolver resolver = Mockito.mock(ChatRuntimeResolver.class);
        when(resolver.resolve(any())).thenThrow(new IllegalArgumentException("Selected model not found"));
        HybridChatExecutionService service = new HybridChatExecutionService(
                null,
                new ChatStreamEventMapper(),
                resolver,
                taskRuns,
                taskEvents
        );

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.execute(new ChatRequest(null, null, null, "hello")).collectList().block())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Selected model not found")
        );

        assertThat(taskRuns.createdTenantId).isNull();
        assertThat(taskEvents.events).isEmpty();
        assertThat(taskRuns.completed).isFalse();
    }

    private static ChatRuntimeResolver runtimeResolver() {
        ChatRuntimeResolver resolver = Mockito.mock(ChatRuntimeResolver.class);
        when(resolver.resolve(any())).thenReturn(new ChatRuntimeSelection(
                mockChatModel(),
                null,
                AgentConfig.defaultAssistant(),
                Optional.empty()
        ));
        return resolver;
    }

    private static ModelConfigEntity mockChatModel() {
        Instant now = Instant.now();
        ModelConfigEntity model = new ModelConfigEntity();
        model.setId(UUID.randomUUID());
        model.setTenantId("tenant-a");
        model.setName("Mock Chat");
        model.setProvider("mock");
        model.setModelType(ModelType.TEXT);
        model.setModelName("mock-chat");
        model.setEnabled(true);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return model;
    }

    private static class RecordingTaskRunRepository implements TaskRunRepository {
        private final UUID id = UUID.randomUUID();
        private String createdTenantId;
        private Intent createdIntent;
        private boolean completed;

        @Override
        public UUID create(String tenantId, ChatRequest request, Intent intent) {
            this.createdTenantId = tenantId;
            this.createdIntent = intent;
            return id;
        }

        @Override
        public void markSuccess(String tenantId, UUID taskRunId, String finalAnswer) {
            this.completed = true;
        }

        @Override
        public void markFailed(String tenantId, UUID taskRunId, String errorMessage) {
        }
    }

    private static class RecordingTaskEventRepository implements TaskEventRepository {
        private final List<Record> events = new ArrayList<>();

        @Override
        public void append(String tenantId, UUID taskRunId, ChatStreamEvent event) {
            events.add(new Record(tenantId, event));
        }
    }

    private record Record(String tenantId, ChatStreamEvent event) {
    }

    private static final class FixedAgentRuntimeFactory extends AgentRuntimeFactory {
        private final AgentScopeStreamEvent event;

        private FixedAgentRuntimeFactory(AgentScopeStreamEvent event) {
            super(new MapSecretResolver(Map.of()), ToolRuntimeRegistrar.noop());
            this.event = event;
        }

        @Override
        public AgentRuntime create(AgentConfig config) {
            return request -> reactor.core.publisher.Flux.just(event);
        }
    }
}
