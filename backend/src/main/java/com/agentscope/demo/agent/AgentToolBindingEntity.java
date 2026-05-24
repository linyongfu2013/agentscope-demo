package com.agentscope.demo.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(AgentToolBindingId.class)
@Table(name = "agent_tool_bindings")
public class AgentToolBindingEntity {

    @Id
    @Column(name = "agent_config_id", nullable = false)
    private UUID agentConfigId;

    @Id
    @Column(name = "tool_config_id", nullable = false)
    private UUID toolConfigId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AgentToolBindingEntity() {
    }

    public AgentToolBindingEntity(UUID agentConfigId, UUID toolConfigId, String tenantId) {
        this.agentConfigId = agentConfigId;
        this.toolConfigId = toolConfigId;
        this.tenantId = tenantId;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
