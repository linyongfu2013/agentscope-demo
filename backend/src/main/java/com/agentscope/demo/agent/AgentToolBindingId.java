package com.agentscope.demo.agent;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AgentToolBindingId implements Serializable {

    private UUID agentConfigId;
    private UUID toolConfigId;

    public AgentToolBindingId() {
    }

    public AgentToolBindingId(UUID agentConfigId, UUID toolConfigId) {
        this.agentConfigId = agentConfigId;
        this.toolConfigId = toolConfigId;
    }

    public UUID getAgentConfigId() {
        return agentConfigId;
    }

    public void setAgentConfigId(UUID agentConfigId) {
        this.agentConfigId = agentConfigId;
    }

    public UUID getToolConfigId() {
        return toolConfigId;
    }

    public void setToolConfigId(UUID toolConfigId) {
        this.toolConfigId = toolConfigId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentToolBindingId that)) {
            return false;
        }
        return Objects.equals(agentConfigId, that.agentConfigId)
                && Objects.equals(toolConfigId, that.toolConfigId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentConfigId, toolConfigId);
    }
}
