package com.agentscope.demo.voice;

import java.time.Instant;
import java.util.UUID;

public record AudioArtifactResponse(
        UUID id,
        String tenantId,
        UUID conversationId,
        UUID messageId,
        AudioDirection direction,
        String storageKey,
        String mimeType,
        Integer durationMs,
        String transcript,
        UUID modelConfigId,
        Instant createdAt
) {
}
