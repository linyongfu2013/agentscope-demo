package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatStreamEventMapperTest {

    private final ChatStreamEventMapper mapper = new ChatStreamEventMapper();

    @Test
    void marksIntermediateAgentScopeEventsCollapsedByDefault() {
        ChatStreamEvent event = mapper.fromAgentScope(
                "run-1",
                AgentScopeStreamEvent.reasoning("thinking delta", false)
        );

        assertThat(event.type()).isEqualTo("reasoning");
        assertThat(event.collapsed()).isTrue();
        assertThat(event.payload()).containsEntry("content", "thinking delta");
    }

    @Test
    void leavesFinalAnswerExpanded() {
        ChatStreamEvent event = mapper.fromAgentScope(
                "run-1",
                AgentScopeStreamEvent.finalAnswer("final markdown")
        );

        assertThat(event.type()).isEqualTo("final_answer");
        assertThat(event.collapsed()).isFalse();
        assertThat(event.payload()).isEqualTo(Map.of("content", "final markdown"));
    }
}
