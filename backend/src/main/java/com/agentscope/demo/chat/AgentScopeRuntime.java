package com.agentscope.demo.chat;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import reactor.core.publisher.Flux;

public class AgentScopeRuntime implements AgentRuntime {

    private final ReActAgent agent;

    public AgentScopeRuntime(ReActAgent agent) {
        this.agent = agent;
    }

    @Override
    public Flux<AgentScopeStreamEvent> stream(ChatRequest request) {
        Msg msg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.prompt())
                .build();
        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.HINT, EventType.SUMMARY, EventType.AGENT_RESULT)
                .incremental(true)
                .build();
        return agent.stream(List.of(msg), options).map(this::mapEvent);
    }

    private AgentScopeStreamEvent mapEvent(Event event) {
        String content = event.getMessage() == null ? "" : event.getMessage().getTextContent();
        if (event.getType() == EventType.REASONING) {
            return AgentScopeStreamEvent.reasoning(content, event.isLast());
        }
        if (event.getType() == EventType.TOOL_RESULT) {
            return AgentScopeStreamEvent.toolResult(content);
        }
        if (event.getType() == EventType.HINT) {
            return AgentScopeStreamEvent.hint(content);
        }
        if (event.getType() == EventType.SUMMARY) {
            return AgentScopeStreamEvent.summary(content);
        }
        return AgentScopeStreamEvent.finalAnswer(content);
    }
}
