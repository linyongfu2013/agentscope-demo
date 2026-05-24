package com.agentscope.demo.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class ChatStreamEventMapper {

    private final AtomicLong sequence = new AtomicLong();

    public ChatStreamEvent fromAgentScope(String runId, AgentScopeStreamEvent source) {
        long next = sequence.incrementAndGet();
        boolean collapsed = !"final_answer".equals(source.type());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", source.content());
        if ("reasoning".equals(source.type())) {
            payload.put("last", source.last());
        }
        return new ChatStreamEvent(
                runId + "-" + next,
                runId,
                next,
                source.type(),
                collapsed,
                Map.copyOf(payload)
        );
    }

    public ChatStreamEvent system(String runId, String type, Map<String, Object> payload) {
        long next = sequence.incrementAndGet();
        return new ChatStreamEvent(runId + "-" + next, runId, next, type, true, payload);
    }
}
