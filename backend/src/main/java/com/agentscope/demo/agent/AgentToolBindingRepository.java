package com.agentscope.demo.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolBindingRepository extends JpaRepository<AgentToolBindingEntity, AgentToolBindingId> {

    List<AgentToolBindingEntity> findByTenantIdAndAgentConfigId(String tenantId, UUID agentConfigId);

    void deleteByTenantIdAndAgentConfigId(String tenantId, UUID agentConfigId);
}
