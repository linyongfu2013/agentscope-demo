package com.agentscope.demo.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ModelConfigRequest(
        @NotBlank String name,
        @NotBlank String provider,
        @NotNull ModelType modelType,
        @NotBlank String modelName,
        String baseUrl,
        String apiKeyRef,
        BigDecimal defaultTemperature,
        Integer defaultMaxTokens,
        ReasoningEffort defaultReasoningEffort,
        Integer embeddingDim,
        List<String> inputModalities,
        List<String> outputModalities,
        String extraParams,
        Boolean enabled
) {
}
