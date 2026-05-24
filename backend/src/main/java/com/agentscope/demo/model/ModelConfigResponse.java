package com.agentscope.demo.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelConfigResponse(
        UUID id,
        String tenantId,
        String name,
        String provider,
        ModelType modelType,
        String modelName,
        String baseUrl,
        String apiKeyRef,
        BigDecimal defaultTemperature,
        Integer defaultMaxTokens,
        ReasoningEffort defaultReasoningEffort,
        Integer embeddingDim,
        List<String> inputModalities,
        List<String> outputModalities,
        String extraParams,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
