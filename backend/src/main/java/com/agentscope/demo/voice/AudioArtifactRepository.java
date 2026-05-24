package com.agentscope.demo.voice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioArtifactRepository extends JpaRepository<AudioArtifactEntity, UUID> {

    Optional<AudioArtifactEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<AudioArtifactEntity> findByTenantIdAndConversationIdOrderByCreatedAtDesc(String tenantId, UUID conversationId);
}
