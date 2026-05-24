package com.agentscope.demo.rag;

import com.agentscope.demo.storage.FileStorage;
import com.agentscope.demo.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class RagUploadService {

    private final KnowledgeRepository knowledgeRepository;
    private final FileStorage fileStorage;
    private final RagIngestionService ragIngestionService;
    private final RagProgressBroadcaster progressBroadcaster;

    public RagUploadService(KnowledgeRepository knowledgeRepository, FileStorage fileStorage,
                            RagIngestionService ragIngestionService, RagProgressBroadcaster progressBroadcaster) {
        this.knowledgeRepository = knowledgeRepository;
        this.fileStorage = fileStorage;
        this.ragIngestionService = ragIngestionService;
        this.progressBroadcaster = progressBroadcaster;
    }

    public KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        String tenantId = TenantContext.currentTenantId();
        UUID id = knowledgeRepository.createKnowledgeBase(
                tenantId,
                request.name(),
                request.description(),
                "deterministic-dev-embedding"
        );
        return new KnowledgeBaseResponse(id, request.name(), "deterministic-dev-embedding");
    }

    public RagFileResponse upload(UUID knowledgeBaseId, String filename, String mimeType, byte[] content) {
        String tenantId = TenantContext.currentTenantId();
        UUID provisionalFileId = UUID.randomUUID();
        String storageKey = tenantId + "/" + knowledgeBaseId + "/" + provisionalFileId + "/" + filename;
        try {
            fileStorage.put(storageKey, new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
        UUID fileId = knowledgeRepository.createFile(tenantId, knowledgeBaseId, storageKey, filename, mimeType);
        RagFileProgress pending = new RagFileProgress(fileId, RagFileStatus.PENDING, 0, 0, 0, null);
        progressBroadcaster.emit(pending);

        Mono.fromRunnable(() -> TenantContext.runWithTenant(tenantId, () -> {
                    try {
                        ragIngestionService.processText(fileId, knowledgeBaseId, filename, content);
                    } catch (RuntimeException e) {
                        ragIngestionService.fail(fileId, e.getMessage());
                    }
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        return new RagFileResponse(fileId, knowledgeBaseId, filename, RagFileStatus.PENDING, 0);
    }
}
