package com.agentscope.demo.rag;

import java.util.UUID;

public record RagFileProgress(
        UUID fileId,
        RagFileStatus status,
        int progress,
        int chunkCount,
        int embeddedChunkCount,
        String errorMessage
) {
}
