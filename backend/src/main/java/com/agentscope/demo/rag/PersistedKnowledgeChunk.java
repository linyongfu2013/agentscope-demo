package com.agentscope.demo.rag;

import java.util.UUID;

public record PersistedKnowledgeChunk(
        String tenantId,
        UUID knowledgeBaseId,
        UUID fileId,
        int chunkIndex,
        String content,
        float[] embedding
) {
}
