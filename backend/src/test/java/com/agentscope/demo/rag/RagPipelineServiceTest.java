package com.agentscope.demo.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagPipelineServiceTest {

    @Test
    void computesEmbeddingProgressFromCompletedChunks() {
        RagFileState state = RagFileState.pending("file-1").startEmbedding(5);

        RagFileState updated = state.markChunkEmbedded().markChunkEmbedded();

        assertThat(updated.status()).isEqualTo(RagFileStatus.EMBEDDING);
        assertThat(updated.embeddedChunkCount()).isEqualTo(2);
        assertThat(updated.progress()).isEqualTo(40);
    }

    @Test
    void allowsOnlyTwoRetriesAfterFailure() {
        RagFileState failed = RagFileState.pending("file-1")
                .fail("parse error")
                .retry()
                .fail("parse error")
                .retry()
                .fail("parse error");

        assertThat(failed.canRetry()).isFalse();
        assertThat(failed.retryCount()).isEqualTo(2);
        assertThat(failed.status()).isEqualTo(RagFileStatus.FAILED);
    }
}
