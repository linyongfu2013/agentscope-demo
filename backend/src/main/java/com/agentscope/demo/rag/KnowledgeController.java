package com.agentscope.demo.rag;

import com.agentscope.demo.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final RagUploadService ragUploadService;
    private final KnowledgeRepository knowledgeRepository;
    private final RagProgressBroadcaster progressBroadcaster;

    public KnowledgeController(RagUploadService ragUploadService, KnowledgeRepository knowledgeRepository,
                               RagProgressBroadcaster progressBroadcaster) {
        this.ragUploadService = ragUploadService;
        this.knowledgeRepository = knowledgeRepository;
        this.progressBroadcaster = progressBroadcaster;
    }

    @PostMapping("/knowledge-bases")
    public KnowledgeBaseResponse createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return ragUploadService.createKnowledgeBase(request);
    }

    @PostMapping(value = "/knowledge-bases/{knowledgeBaseId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RagFileResponse> upload(@PathVariable UUID knowledgeBaseId, @RequestPart("file") FilePart file) {
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> toBytes(dataBuffer))
                .map(bytes -> ragUploadService.upload(knowledgeBaseId, file.filename(),
                        file.headers().getContentType() == null ? "application/octet-stream" : file.headers().getContentType().toString(),
                        bytes));
    }

    @GetMapping(value = "/knowledge-files/{fileId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RagFileProgress>> progress(@PathVariable UUID fileId) {
        String tenantId = TenantContext.currentTenantId();
        Flux<RagFileProgress> current = knowledgeRepository.findFileProgress(tenantId, fileId)
                .map(Flux::just)
                .orElseGet(Flux::empty);
        return current.concatWith(progressBroadcaster.stream(fileId))
                .map(progress -> ServerSentEvent.<RagFileProgress>builder()
                        .event(progress.status().name().toLowerCase())
                        .data(progress)
                        .build());
    }

    private byte[] toBytes(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return bytes;
    }
}
