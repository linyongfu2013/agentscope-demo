package com.agentscope.demo.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class HybridChatExecutionService {

    private final AgentRuntimeFactory agentRuntimeFactory;
    private final ChatStreamEventMapper mapper;
    private final ChatRuntimeResolver runtimeResolver;
    private final TaskRunRepository taskRunRepository;
    private final TaskEventRepository taskEventRepository;

    public HybridChatExecutionService(AgentRuntimeFactory agentRuntimeFactory, ChatStreamEventMapper mapper,
                                      ChatRuntimeResolver runtimeResolver, TaskRunRepository taskRunRepository,
                                      TaskEventRepository taskEventRepository) {
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.mapper = mapper;
        this.runtimeResolver = runtimeResolver;
        this.taskRunRepository = taskRunRepository;
        this.taskEventRepository = taskEventRepository;
    }

    public Flux<ChatStreamEvent> execute(ChatRequest request) {
        String tenantId = com.agentscope.demo.tenant.TenantContext.currentTenantId();
        ChatRuntimeSelection selection = runtimeResolver.resolve(request);
        UUID taskRunId = taskRunRepository.create(tenantId, request, Intent.SELF_DIRECTED);
        String runId = taskRunId.toString();
        AtomicReference<String> finalAnswer = new AtomicReference<>("");
        ChatStreamEvent strategy = mapper.system(runId, "execution_strategy", Map.of(
                "strategy", "model_self_directed",
                "content", "Model decides whether to answer directly or plan and execute in the same run."
        ));
        ChatStreamEvent modelResolved = mapper.system(runId, "model_resolved", Map.of(
                "content", "Model: " + selection.model().getName(),
                "modelId", selection.model().getId().toString()
        ));
        Flux<ChatStreamEvent> prefix = selection.warning()
                .map(warning -> Flux.just(strategy, modelResolved, mapper.system(runId, "warning", Map.of("content", warning))))
                .orElseGet(() -> Flux.just(strategy, modelResolved));
        Flux<ChatStreamEvent> stream;
        stream = Flux.concat(
                prefix,
                agentRuntimeFactory.create(selection.agentConfig()).stream(request).map(event -> mapper.fromAgentScope(runId, event))
        );
        return stream
                .doOnNext(event -> {
                    if ("final_answer".equals(event.type())) {
                        finalAnswer.set(String.valueOf(event.payload().getOrDefault("content", "")));
                    }
                    taskEventRepository.append(tenantId, taskRunId, event);
                })
                .doOnComplete(() -> taskRunRepository.markSuccess(tenantId, taskRunId, finalAnswer.get()))
                .doOnError(error -> taskRunRepository.markFailed(tenantId, taskRunId, error.getMessage()));
    }
}
