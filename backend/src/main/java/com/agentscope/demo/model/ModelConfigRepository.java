package com.agentscope.demo.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, UUID> {

    boolean existsByTenantIdAndName(String tenantId, String name);

    List<ModelConfigEntity> findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(String tenantId);

    List<ModelConfigEntity> findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(
            String tenantId,
            ModelType modelType
    );

    Optional<ModelConfigEntity> findByTenantIdAndId(String tenantId, UUID id);

    void deleteByTenantIdAndId(String tenantId, UUID id);
}
