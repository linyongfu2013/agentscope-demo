package com.agentscope.demo.rag;

import java.util.UUID;
import java.util.Optional;

public interface KnowledgeRepository {

    default UUID createKnowledgeBase(String tenantId, String name, String description, String embeddingModel) {
        throw new UnsupportedOperationException("createKnowledgeBase is not implemented");
    }

    default UUID createFile(String tenantId, UUID knowledgeBaseId, String storageKey, String filename, String mimeType) {
        throw new UnsupportedOperationException("createFile is not implemented");
    }

    default boolean existsKnowledgeBase(String tenantId, UUID knowledgeBaseId) {
        throw new UnsupportedOperationException("existsKnowledgeBase is not implemented");
    }

    void updateFileState(String tenantId, UUID fileId, RagFileStatus status, int progress,
                         int chunkCount, int embeddedChunkCount, String errorMessage);

    void insertChunk(PersistedKnowledgeChunk chunk);

    default Optional<RagFileProgress> findFileProgress(String tenantId, UUID fileId) {
        return Optional.empty();
    }
}
