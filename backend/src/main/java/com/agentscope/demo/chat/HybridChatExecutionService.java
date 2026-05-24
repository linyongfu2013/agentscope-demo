package com.agentscope.demo.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class HybridChatExecutionService {

    private final IntentClassifier intentClassifier;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final ChatStreamEventMapper mapper;
    private final ChatRuntimeResolver runtimeResolver;
    private final TaskRunRepository taskRunRepository;
    private final TaskEventRepository taskEventRepository;

    public HybridChatExecutionService(IntentClassifier intentClassifier, AgentRuntimeFactory agentRuntimeFactory, ChatStreamEventMapper mapper,
                                      ChatRuntimeResolver runtimeResolver, TaskRunRepository taskRunRepository,
                                      TaskEventRepository taskEventRepository) {
        this.intentClassifier = intentClassifier;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.mapper = mapper;
        this.runtimeResolver = runtimeResolver;
        this.taskRunRepository = taskRunRepository;
        this.taskEventRepository = taskEventRepository;
    }

    public Flux<ChatStreamEvent> execute(ChatRequest request) {
        String tenantId = com.agentscope.demo.tenant.TenantContext.currentTenantId();
        ChatRuntimeSelection selection = runtimeResolver.resolve(request);
        Intent intent = intentClassifier.classify(request.prompt());
        UUID taskRunId = taskRunRepository.create(tenantId, request, intent);
        String runId = taskRunId.toString();
        AtomicReference<String> finalAnswer = new AtomicReference<>("");
        ChatStreamEvent route = mapper.system(runId, "intent", Map.of("intent", intent.name()));
        ChatStreamEvent modelResolved = mapper.system(runId, "model_resolved", Map.of(
                "content", "Model: " + selection.model().getName(),
                "modelId", selection.model().getId().toString()
        ));
        Flux<ChatStreamEvent> prefix = selection.warning()
                .map(warning -> Flux.just(route, modelResolved, mapper.system(runId, "warning", Map.of("content", warning))))
                .orElseGet(() -> Flux.just(route, modelResolved));
        Flux<ChatStreamEvent> stream;
        if (intent == Intent.SIMPLE) {
            stream = Flux.concat(
                    prefix,
                    Flux.just(mapper.fromAgentScope(runId, AgentScopeStreamEvent.finalAnswer("Echo: " + request.prompt())))
            );
        } else {
            stream = Flux.concat(
                    prefix,
                    agentRuntimeFactory.create(selection.agentConfig()).stream(request).map(event -> mapper.fromAgentScope(runId, event))
            );
        }
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
