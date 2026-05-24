package com.agentscope.demo.rag;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeBaseCreateRequest(
        @NotBlank String name,
        String description
) {
}
