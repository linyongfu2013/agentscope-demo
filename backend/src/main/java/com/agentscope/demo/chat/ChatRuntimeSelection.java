package com.agentscope.demo.chat;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ReasoningEffort;
import java.util.Optional;

public record ChatRuntimeSelection(
        ModelConfigEntity model,
        ReasoningEffort reasoningEffort,
        AgentConfig agentConfig,
        Optional<String> warning
) {
    public ChatRuntimeSelection {
        warning = warning == null ? Optional.empty() : warning;
    }
}
