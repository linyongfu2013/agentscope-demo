package com.agentscope.demo.toolconfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolConfigRepository extends JpaRepository<ToolConfigEntity, UUID> {

    boolean existsByTenantIdAndName(String tenantId, String name);

    List<ToolConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId);

    List<ToolConfigEntity> findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc(
            String tenantId,
            ToolType toolType
    );

    Optional<ToolConfigEntity> findByTenantIdAndId(String tenantId, UUID id);

    void deleteByTenantIdAndId(String tenantId, UUID id);
}
