package com.agentscope.demo.model;

import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelConfigRepository repository;
    private final TenantRepository tenantRepository;

    public ModelConfigService(ModelConfigRepository repository, TenantRepository tenantRepository) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public ModelConfigResponse create(ModelConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        tenantRepository.ensureExists(tenantId);
        if (repository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new IllegalArgumentException("model config name already exists in tenant");
        }

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setProvider(request.provider());
        entity.setModelType(request.modelType());
        entity.setModelName(request.modelName());
        entity.setBaseUrl(request.baseUrl());
        entity.setApiKeyRef(request.apiKeyRef());
        entity.setDefaultTemperature(request.defaultTemperature());
        entity.setDefaultMaxTokens(request.defaultMaxTokens());
        entity.setDefaultReasoningEffort(request.defaultReasoningEffort());
        entity.setEmbeddingDim(request.embeddingDim());
        entity.setInputModalities(toArrayJson(request.inputModalities()));
        entity.setOutputModalities(toArrayJson(request.outputModalities()));
        entity.setExtraParams(ModelConfigEntity.parseJson(request.extraParams(), ModelConfigEntity.emptyObject()));
        entity.setEnabled(request.enabled() == null || request.enabled());

        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ModelConfigResponse> list(ModelType type) {
        String tenantId = TenantContext.currentTenantId();
        List<ModelConfigEntity> entities = type == null
                ? repository.findByTenantIdAndEnabledTrueOrderByCreatedAtDesc(tenantId)
                : repository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(tenantId, type);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ModelConfigResponse update(UUID id, ModelConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        ModelConfigEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("model config not found"));

        entity.setName(request.name());
        entity.setProvider(request.provider());
        entity.setModelType(request.modelType());
        entity.setModelName(request.modelName());
        entity.setBaseUrl(request.baseUrl());
        entity.setApiKeyRef(request.apiKeyRef());
        entity.setDefaultTemperature(request.defaultTemperature());
        entity.setDefaultMaxTokens(request.defaultMaxTokens());
        entity.setDefaultReasoningEffort(request.defaultReasoningEffort());
        entity.setEmbeddingDim(request.embeddingDim());
        entity.setInputModalities(toArrayJson(request.inputModalities()));
        entity.setOutputModalities(toArrayJson(request.outputModalities()));
        entity.setExtraParams(ModelConfigEntity.parseJson(request.extraParams(), ModelConfigEntity.emptyObject()));
        entity.setEnabled(request.enabled() == null || request.enabled());

        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteByTenantIdAndId(TenantContext.currentTenantId(), id);
    }

    private ModelConfigResponse toResponse(ModelConfigEntity entity) {
        return new ModelConfigResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getProvider(),
                entity.getModelType(),
                entity.getModelName(),
                entity.getBaseUrl(),
                entity.getApiKeyRef(),
                entity.getDefaultTemperature(),
                entity.getDefaultMaxTokens(),
                entity.getDefaultReasoningEffort(),
                entity.getEmbeddingDim(),
                toStringList(entity.getInputModalities()),
                toStringList(entity.getOutputModalities()),
                toJsonString(entity.getExtraParams()),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private JsonNode toArrayJson(List<String> values) {
        return OBJECT_MAPPER.valueToTree(values == null ? Collections.emptyList() : values);
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private String toJsonString(JsonNode node) {
        JsonNode value = node == null ? ModelConfigEntity.emptyObject() : node;
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize model config JSON", e);
        }
    }
}
