package com.agentscope.demo.agent;

import com.agentscope.demo.model.ReasoningEffort;
import java.util.List;
import java.util.UUID;

public record AgentConfigRequest(
        String name,
        String systemPrompt,
        UUID primaryModelId,
        UUID reasoningModelId,
        UUID embeddingModelId,
        ReasoningEffort defaultReasoningEffort,
        List<UUID> toolIds,
        List<UUID> knowledgeBaseIds
) {
}
