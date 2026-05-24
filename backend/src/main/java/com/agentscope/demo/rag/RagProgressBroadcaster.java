package com.agentscope.demo.rag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class RagProgressBroadcaster {

    private final Map<UUID, Sinks.Many<RagFileProgress>> sinks = new ConcurrentHashMap<>();

    public void emit(RagFileProgress progress) {
        sink(progress.fileId()).tryEmitNext(progress);
        if (progress.status() == RagFileStatus.SUCCESS || progress.status() == RagFileStatus.FAILED) {
            sink(progress.fileId()).tryEmitComplete();
        }
    }

    public Flux<RagFileProgress> stream(UUID fileId) {
        return sink(fileId).asFlux();
    }

    private Sinks.Many<RagFileProgress> sink(UUID fileId) {
        return sinks.computeIfAbsent(fileId, ignored -> Sinks.many().multicast().directBestEffort());
    }
}
