package com.agentscope.demo.chat;

import java.util.List;
import java.util.UUID;

public record AgentConfig(
        UUID id,
        String name,
        String systemPrompt,
        String modelName,
        double temperature,
        int maxTokens,
        List<String> tools,
        List<UUID> knowledgeBaseIds
) {
    public static AgentConfig defaultAssistant() {
        return new AgentConfig(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Enterprise Assistant",
                "You are an enterprise assistant. For complex tasks, create a task plan, execute subtasks, call tools when useful, and summarize observations before the final answer.",
                "deepseek-chat",
                0.7,
                4096,
                List.of("calculator"),
                List.of()
        );
    }
}
