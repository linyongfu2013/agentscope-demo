package com.agentscope.demo.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagIngestionServiceTest {

    @Test
    void parsesChunksEmbedsAndPersistsProgressByTenant() {
        RecordingKnowledgeRepository repository = new RecordingKnowledgeRepository();
        RagIngestionService service = new RagIngestionService(
                repository,
                new PlainTextDocumentParser(),
                new DeterministicEmbeddingClient()
        );
        UUID knowledgeBaseId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        TenantContext.runWithTenant("tenant-a", () ->
                service.processText(fileId, knowledgeBaseId, "handbook.md",
                        "First paragraph\n\nSecond paragraph".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(repository.statuses.getFirst()).isEqualTo(RagFileStatus.PARSING);
        assertThat(repository.statuses).contains(RagFileStatus.EMBEDDING);
        assertThat(repository.statuses.getLast()).isEqualTo(RagFileStatus.SUCCESS);
        assertThat(repository.chunks).hasSize(2);
        assertThat(repository.chunks).allSatisfy(chunk -> {
            assertThat(chunk.tenantId()).isEqualTo("tenant-a");
            assertThat(chunk.embedding()).hasSize(1536);
        });
        assertThat(repository.lastProgress).isEqualTo(100);
    }

    private static class RecordingKnowledgeRepository implements KnowledgeRepository {
        private final List<RagFileStatus> statuses = new ArrayList<>();
        private final List<PersistedKnowledgeChunk> chunks = new ArrayList<>();
        private int lastProgress;

        @Override
        public void updateFileState(String tenantId, UUID fileId, RagFileStatus status, int progress,
                                    int chunkCount, int embeddedChunkCount, String errorMessage) {
            statuses.add(status);
            this.lastProgress = progress;
        }

        @Override
        public void insertChunk(PersistedKnowledgeChunk chunk) {
            chunks.add(chunk);
        }
    }
}
