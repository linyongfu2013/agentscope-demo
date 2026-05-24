package com.agentscope.demo.chat;

import java.time.Duration;
import reactor.core.publisher.Flux;

public class MockAgentRuntime implements AgentRuntime {

    @Override
    public Flux<AgentScopeStreamEvent> stream(ChatRequest request) {
        return Flux.just(
                        AgentScopeStreamEvent.summary("Task Plan: classify intent, retrieve relevant knowledge, execute tools, synthesize answer."),
                        AgentScopeStreamEvent.reasoning("Sub-task Execution: prepare local development response.", true),
                        AgentScopeStreamEvent.toolResult("Observation: DEEPSEEK_APIKEY is not configured, using local mock runtime."),
                        AgentScopeStreamEvent.finalAnswer("已进入 Agent 执行路径。当前本地未配置模型密钥时，会返回 mock 流用于调试前端 SSE 和折叠日志。")
                )
                .delayElements(Duration.ofMillis(20));
    }
}
