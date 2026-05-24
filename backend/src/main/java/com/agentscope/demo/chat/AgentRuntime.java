package com.agentscope.demo.chat;

import reactor.core.publisher.Flux;

public interface AgentRuntime {

    Flux<AgentScopeStreamEvent> stream(ChatRequest request);
}
