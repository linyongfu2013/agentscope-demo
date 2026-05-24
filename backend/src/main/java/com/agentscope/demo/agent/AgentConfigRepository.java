package com.agentscope.demo.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConfigRepository extends JpaRepository<AgentConfigEntity, UUID> {

    Optional<AgentConfigEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<AgentConfigEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    void deleteByTenantIdAndId(String tenantId, UUID id);
}
