package com.agentscope.demo.rag;

import com.agentscope.demo.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RagIngestionService {

    private final KnowledgeRepository knowledgeRepository;
    private final DocumentParser documentParser;
    private final EmbeddingClient embeddingClient;
    private final RagProgressBroadcaster progressBroadcaster;

    public RagIngestionService(KnowledgeRepository knowledgeRepository, DocumentParser documentParser, EmbeddingClient embeddingClient) {
        this(knowledgeRepository, documentParser, embeddingClient, null);
    }

    @Autowired
    public RagIngestionService(KnowledgeRepository knowledgeRepository, DocumentParser documentParser, EmbeddingClient embeddingClient,
                               RagProgressBroadcaster progressBroadcaster) {
        this.knowledgeRepository = knowledgeRepository;
        this.documentParser = documentParser;
        this.embeddingClient = embeddingClient;
        this.progressBroadcaster = progressBroadcaster;
    }

    public void processText(UUID fileId, UUID knowledgeBaseId, String filename, byte[] content) {
        String tenantId = TenantContext.currentTenantId();
        update(tenantId, fileId, RagFileStatus.PARSING, 5, 0, 0, null);
        List<String> chunks = documentParser.parse(content, filename);
        update(tenantId, fileId, RagFileStatus.EMBEDDING, 0, chunks.size(), 0, null);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            knowledgeRepository.insertChunk(new PersistedKnowledgeChunk(
                    tenantId,
                    knowledgeBaseId,
                    fileId,
                    i,
                    chunk,
                    embeddingClient.embed(chunk)
            ));
            int completed = i + 1;
            int progress = chunks.isEmpty() ? 100 : (completed * 100) / chunks.size();
            RagFileStatus status = completed == chunks.size() ? RagFileStatus.SUCCESS : RagFileStatus.EMBEDDING;
            update(tenantId, fileId, status, progress, chunks.size(), completed, null);
        }
        if (chunks.isEmpty()) {
            update(tenantId, fileId, RagFileStatus.SUCCESS, 100, 0, 0, null);
        }
    }

    public void fail(UUID fileId, String message) {
        update(TenantContext.currentTenantId(), fileId, RagFileStatus.FAILED, 0, 0, 0, message);
    }

    private void update(String tenantId, UUID fileId, RagFileStatus status, int progress,
                        int chunkCount, int embeddedChunkCount, String errorMessage) {
        knowledgeRepository.updateFileState(tenantId, fileId, status, progress, chunkCount, embeddedChunkCount, errorMessage);
        if (progressBroadcaster != null) {
            progressBroadcaster.emit(new RagFileProgress(fileId, status, progress, chunkCount, embeddedChunkCount, errorMessage));
        }
    }
}
