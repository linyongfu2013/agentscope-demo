package com.agentscope.demo.rag;

import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RagPipelineService {

    public Flux<RagFileState> processText(String fileId, String text) {
        List<String> chunks = chunk(text);
        RagFileState initial = RagFileState.pending(fileId).startParsing().startEmbedding(chunks.size());
        return Flux.range(0, chunks.size())
                .scan(initial, (state, ignored) -> state.markChunkEmbedded());
    }

    private List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("(?<=\\G.{800})"));
    }
}
