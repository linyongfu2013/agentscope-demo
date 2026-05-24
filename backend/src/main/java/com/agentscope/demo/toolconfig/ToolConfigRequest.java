package com.agentscope.demo.toolconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolConfigRequest(
        @NotBlank String name,
        String description,
        @NotNull ToolType toolType,
        String endpoint,
        ToolAuthType authType,
        String authRef,
        String inputSchema,
        String config,
        Integer timeoutMs,
        Boolean enabled
) {
}
