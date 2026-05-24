package com.agentscope.demo.rag;

import java.util.UUID;

public record RagFileResponse(
        UUID fileId,
        UUID knowledgeBaseId,
        String filename,
        RagFileStatus status,
        int progress
) {
}
