package com.agentscope.demo.chat;

public record AgentScopeStreamEvent(
        String type,
        String content,
        boolean last
) {
    public static AgentScopeStreamEvent reasoning(String content, boolean last) {
        return new AgentScopeStreamEvent("reasoning", content, last);
    }

    public static AgentScopeStreamEvent toolResult(String content) {
        return new AgentScopeStreamEvent("tool_result", content, true);
    }

    public static AgentScopeStreamEvent hint(String content) {
        return new AgentScopeStreamEvent("rag_hint", content, true);
    }

    public static AgentScopeStreamEvent summary(String content) {
        return new AgentScopeStreamEvent("task_summary", content, true);
    }

    public static AgentScopeStreamEvent finalAnswer(String content) {
        return new AgentScopeStreamEvent("final_answer", content, true);
    }
}
