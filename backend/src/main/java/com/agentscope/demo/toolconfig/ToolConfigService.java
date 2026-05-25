package com.agentscope.demo.toolconfig;

import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolConfigService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolConfigRepository repository;
    private final TenantRepository tenantRepository;
    private final ToolRuntimeClient runtimeClient;

    @Autowired
    public ToolConfigService(ToolConfigRepository repository, TenantRepository tenantRepository, ToolRuntimeClient runtimeClient) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.runtimeClient = runtimeClient;
    }

    ToolConfigService(ToolConfigRepository repository, TenantRepository tenantRepository) {
        this(repository, tenantRepository, new ToolRuntimeClient() {
            @Override
            public ToolInvocationResult test(ToolConfigEntity entity) {
                return new ToolInvocationResult(true,
                        "mock observation for " + entity.getToolType() + " tool " + entity.getName());
            }

            @Override
            public ToolInvocationResult invoke(ToolConfigEntity entity, java.util.Map<String, Object> input) {
                return test(entity);
            }
        });
    }

    @Transactional
    public ToolConfigResponse create(ToolConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        tenantRepository.ensureExists(tenantId);
        if (repository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new IllegalArgumentException("tool config name already exists in tenant");
        }

        ToolConfigEntity entity = new ToolConfigEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setToolType(request.toolType());
        entity.setEndpoint(request.endpoint());
        entity.setAuthType(request.authType());
        entity.setAuthRef(request.authRef());
        entity.setInputSchema(ToolConfigEntity.parseJson(request.inputSchema(), ToolConfigEntity.emptyObject()));
        entity.setConfig(ToolConfigEntity.parseJson(request.config(), ToolConfigEntity.emptyObject()));
        entity.setTimeoutMs(request.timeoutMs() == null ? 30000 : request.timeoutMs());
        entity.setEnabled(request.enabled() == null || request.enabled());

        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ToolConfigResponse> list(ToolType type) {
        String tenantId = TenantContext.currentTenantId();
        List<ToolConfigEntity> entities = type == null
                ? repository.findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(tenantId)
                : repository.findByTenantIdAndToolTypeAndEnabledTrueOrderByCreatedAtDesc(tenantId, type);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ToolConfigResponse update(UUID id, ToolConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        ToolConfigEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("tool config not found"));

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setToolType(request.toolType());
        entity.setEndpoint(request.endpoint());
        entity.setAuthType(request.authType());
        entity.setAuthRef(request.authRef());
        entity.setInputSchema(ToolConfigEntity.parseJson(request.inputSchema(), ToolConfigEntity.emptyObject()));
        entity.setConfig(ToolConfigEntity.parseJson(request.config(), ToolConfigEntity.emptyObject()));
        entity.setTimeoutMs(request.timeoutMs() == null ? 30000 : request.timeoutMs());
        entity.setEnabled(request.enabled() == null || request.enabled());

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteByTenantIdAndId(TenantContext.currentTenantId(), id);
    }

    @Transactional(readOnly = true)
    public ToolTestResponse test(UUID id) {
        String tenantId = TenantContext.currentTenantId();
        ToolConfigEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("tool config not found"));
        ToolInvocationResult result = runtimeClient.test(entity);
        return new ToolTestResponse(result.success(), result.observation());
    }

    private ToolConfigResponse toResponse(ToolConfigEntity entity) {
        return new ToolConfigResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDescription(),
                entity.getToolType(),
                entity.getEndpoint(),
                entity.getAuthType(),
                entity.getAuthRef(),
                toJsonString(entity.getInputSchema()),
                toJsonString(entity.getConfig()),
                entity.getTimeoutMs(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJsonString(JsonNode node) {
        JsonNode value = node == null ? ToolConfigEntity.emptyObject() : node;
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize tool config JSON", e);
        }
    }
}
