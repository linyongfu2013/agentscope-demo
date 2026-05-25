package com.agentscope.demo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.secret.MapSecretResolver;
import com.agentscope.demo.toolconfig.ToolRuntimeRegistrar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentRuntimeFactoryTest {

    @Test
    void createsRealAgentScopeRuntimeFromConfiguredOpenAiCompatibleModel() {
        AgentRuntimeFactory factory = new AgentRuntimeFactory(
                new MapSecretResolver(Map.of("OPENAI_API_KEY", "sk-test")),
                ToolRuntimeRegistrar.noop()
        );

        AgentConfig config = new AgentConfig(
                UUID.randomUUID(),
                "Assistant",
                "Answer directly.",
                "openai",
                "gpt-4.1-mini",
                "https://api.openai.com/v1",
                "OPENAI_API_KEY",
                0.2,
                1024,
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(factory.create(config)).isInstanceOf(AgentScopeRuntime.class);
    }

    @Test
    void fallsBackToMockRuntimeWhenConfiguredModelSecretIsMissing() {
        AgentRuntimeFactory factory = new AgentRuntimeFactory(
                new MapSecretResolver(Map.of()),
                ToolRuntimeRegistrar.noop()
        );

        AgentConfig config = new AgentConfig(
                UUID.randomUUID(),
                "Assistant",
                "Answer directly.",
                "openai",
                "gpt-4.1-mini",
                "https://api.openai.com/v1",
                "OPENAI_API_KEY",
                0.2,
                1024,
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(factory.create(config)).isInstanceOf(MockAgentRuntime.class);
    }
}
