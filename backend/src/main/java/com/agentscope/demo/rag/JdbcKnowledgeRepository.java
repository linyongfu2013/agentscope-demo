package com.agentscope.demo.rag;

import com.agentscope.demo.tenant.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeRepository implements KnowledgeRepository {

    private final JdbcClient jdbcClient;
    private final TenantRepository tenantRepository;

    public JdbcKnowledgeRepository(JdbcClient jdbcClient, TenantRepository tenantRepository) {
        this.jdbcClient = jdbcClient;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public UUID createKnowledgeBase(String tenantId, String name, String description, String embeddingModel) {
        tenantRepository.ensureExists(tenantId);
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO knowledge_bases (id, tenant_id, name, description, embedding_model, embedding_dim)
                        VALUES (:id, :tenantId, :name, :description, :embeddingModel, :embeddingDim)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("name", name)
                .param("description", description)
                .param("embeddingModel", embeddingModel)
                .param("embeddingDim", DeterministicEmbeddingClient.DIMENSIONS)
                .update();
        return id;
    }

    @Override
    public UUID createFile(String tenantId, UUID knowledgeBaseId, String storageKey, String filename, String mimeType) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO knowledge_files
                            (id, tenant_id, knowledge_base_id, storage_key, filename, mime_type, status, progress, max_retry_count)
                        VALUES
                            (:id, :tenantId, :knowledgeBaseId, :storageKey, :filename, :mimeType,
                             CAST('PENDING' AS kb_file_status), 0, 2)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("knowledgeBaseId", knowledgeBaseId)
                .param("storageKey", storageKey)
                .param("filename", filename)
                .param("mimeType", mimeType)
                .update();
        return id;
    }

    @Override
    public boolean existsKnowledgeBase(String tenantId, UUID knowledgeBaseId) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM knowledge_bases
                            WHERE tenant_id = :tenantId AND id = :knowledgeBaseId
                        )
                        """)
                .param("tenantId", tenantId)
                .param("knowledgeBaseId", knowledgeBaseId)
                .query(Boolean.class)
                .single());
    }

    @Override
    public void updateFileState(String tenantId, UUID fileId, RagFileStatus status, int progress,
                                int chunkCount, int embeddedChunkCount, String errorMessage) {
        jdbcClient.sql("""
                        UPDATE knowledge_files
                        SET status = CAST(:status AS kb_file_status),
                            progress = :progress,
                            chunk_count = :chunkCount,
                            embedded_chunk_count = :embeddedChunkCount,
                            error_message = :errorMessage,
                            updated_at = now()
                        WHERE tenant_id = :tenantId AND id = :fileId
                        """)
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .param("status", status.name())
                .param("progress", progress)
                .param("chunkCount", chunkCount)
                .param("embeddedChunkCount", embeddedChunkCount)
                .param("errorMessage", errorMessage)
                .update();
    }

    @Override
    public void insertChunk(PersistedKnowledgeChunk chunk) {
        jdbcClient.sql("""
                        INSERT INTO knowledge_chunks
                            (tenant_id, knowledge_base_id, file_id, chunk_index, content, embedding)
                        VALUES
                            (:tenantId, :knowledgeBaseId, :fileId, :chunkIndex, :content, CAST(:embedding AS vector))
                        ON CONFLICT (file_id, chunk_index)
                        DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
                        """)
                .param("tenantId", chunk.tenantId())
                .param("knowledgeBaseId", chunk.knowledgeBaseId())
                .param("fileId", chunk.fileId())
                .param("chunkIndex", chunk.chunkIndex())
                .param("content", chunk.content())
                .param("embedding", vectorLiteral(chunk.embedding()))
                .update();
    }

    @Override
    public Optional<RagFileProgress> findFileProgress(String tenantId, UUID fileId) {
        return jdbcClient.sql("""
                        SELECT id, status, progress, chunk_count, embedded_chunk_count, error_message
                        FROM knowledge_files
                        WHERE tenant_id = :tenantId AND id = :fileId
                        """)
                .param("tenantId", tenantId)
                .param("fileId", fileId)
                .query((rs, rowNum) -> new RagFileProgress(
                        rs.getObject("id", UUID.class),
                        RagFileStatus.valueOf(rs.getString("status")),
                        rs.getInt("progress"),
                        rs.getInt("chunk_count"),
                        rs.getInt("embedded_chunk_count"),
                        rs.getString("error_message")
                ))
                .optional();
    }

    private String vectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }
}
