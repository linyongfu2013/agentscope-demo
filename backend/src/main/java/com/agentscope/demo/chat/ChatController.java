package com.agentscope.demo.chat;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HybridChatExecutionService chatExecutionService;

    public ChatController(HybridChatExecutionService chatExecutionService) {
        this.chatExecutionService = chatExecutionService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        return chatExecutionService.execute(request)
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder()
                        .id(event.id())
                        .event(event.type())
                        .data(event)
                        .build());
    }
}
