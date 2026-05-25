package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentExecutionPromptTest {

    @Test
    void appendsSelfDirectedExecutionPolicyToAgentPrompt() {
        String prompt = AgentExecutionPrompt.apply("Base instructions.");

        assertThat(prompt).contains("Base instructions.");
        assertThat(prompt).contains("If the user asks a simple question, answer directly");
        assertThat(prompt).contains("If the user asks for a complex task");
        assertThat(prompt).contains("create a plan");
        assertThat(prompt).contains("execute the plan");
    }

    @Test
    void doesNotDuplicatePolicyWhenAppliedTwice() {
        String prompt = AgentExecutionPrompt.apply(AgentExecutionPrompt.apply("Base instructions."));

        assertThat(prompt).containsOnlyOnce(AgentExecutionPrompt.MARKER);
    }
}
