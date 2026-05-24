package com.agentscope.demo.rag;

import java.util.UUID;

public record KnowledgeBaseResponse(UUID id, String name, String embeddingModel) {
}
