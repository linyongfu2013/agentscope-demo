package com.agentscope.demo.toolconfig;

import java.time.Instant;
import java.util.UUID;

public record ToolConfigResponse(
        UUID id,
        String tenantId,
        String name,
        String description,
        ToolType toolType,
        String endpoint,
        ToolAuthType authType,
        String authRef,
        String inputSchema,
        String config,
        int timeoutMs,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
