package com.agentscope.demo.rag;

public record RagFileState(
        String fileId,
        RagFileStatus status,
        int progress,
        int chunkCount,
        int embeddedChunkCount,
        int retryCount,
        int maxRetryCount,
        String errorMessage
) {
    public static RagFileState pending(String fileId) {
        return new RagFileState(fileId, RagFileStatus.PENDING, 0, 0, 0, 0, 2, null);
    }

    public RagFileState startParsing() {
        return new RagFileState(fileId, RagFileStatus.PARSING, 5, 0, 0, retryCount, maxRetryCount, null);
    }

    public RagFileState startEmbedding(int chunks) {
        if (chunks <= 0) {
            return new RagFileState(fileId, RagFileStatus.SUCCESS, 100, 0, 0, retryCount, maxRetryCount, null);
        }
        return new RagFileState(fileId, RagFileStatus.EMBEDDING, 0, chunks, 0, retryCount, maxRetryCount, null);
    }

    public RagFileState markChunkEmbedded() {
        int completed = Math.min(embeddedChunkCount + 1, chunkCount);
        int nextProgress = chunkCount == 0 ? 100 : (completed * 100) / chunkCount;
        RagFileStatus nextStatus = completed >= chunkCount ? RagFileStatus.SUCCESS : RagFileStatus.EMBEDDING;
        return new RagFileState(fileId, nextStatus, nextProgress, chunkCount, completed, retryCount, maxRetryCount, null);
    }

    public RagFileState fail(String message) {
        return new RagFileState(fileId, RagFileStatus.FAILED, progress, chunkCount, embeddedChunkCount, retryCount, maxRetryCount, message);
    }

    public boolean canRetry() {
        return status == RagFileStatus.FAILED && retryCount < maxRetryCount;
    }

    public RagFileState retry() {
        if (!canRetry()) {
            return this;
        }
        return new RagFileState(fileId, RagFileStatus.PENDING, 0, 0, 0, retryCount + 1, maxRetryCount, null);
    }
}
