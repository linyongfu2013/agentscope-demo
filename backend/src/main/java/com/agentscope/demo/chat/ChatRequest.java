package com.agentscope.demo.chat;

import com.agentscope.demo.model.ReasoningEffort;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
        UUID conversationId,
        UUID agentConfigId,
        UUID userId,
        @NotBlank String prompt,
        UUID modelId,
        ReasoningEffort reasoningEffort,
        List<UUID> toolIds,
        UUID sttModelId,
        UUID ttsModelId,
        UUID audioArtifactId
) {
    public ChatRequest {
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }

    public ChatRequest(UUID conversationId, UUID agentConfigId, UUID userId, String prompt) {
        this(conversationId, agentConfigId, userId, prompt, null, null, List.of(), null, null, null);
    }
}
