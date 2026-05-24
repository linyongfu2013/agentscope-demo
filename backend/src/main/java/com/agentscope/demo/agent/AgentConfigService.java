package com.agentscope.demo.agent;

import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.rag.KnowledgeRepository;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import com.agentscope.demo.toolconfig.ToolConfigRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentConfigService {

    private final AgentConfigRepository agentRepository;
    private final AgentToolBindingRepository bindingRepository;
    private final TenantRepository tenantRepository;
    private final ModelConfigRepository modelRepository;
    private final ToolConfigRepository toolRepository;
    private final KnowledgeRepository knowledgeRepository;

    public AgentConfigService(
            AgentConfigRepository agentRepository,
            AgentToolBindingRepository bindingRepository,
            TenantRepository tenantRepository,
            ModelConfigRepository modelRepository,
            ToolConfigRepository toolRepository,
            KnowledgeRepository knowledgeRepository
    ) {
        this.agentRepository = agentRepository;
        this.bindingRepository = bindingRepository;
        this.tenantRepository = tenantRepository;
        this.modelRepository = modelRepository;
        this.toolRepository = toolRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    @Transactional
    public AgentConfigResponse create(AgentConfigRequest request) {
        String tenantId = TenantContext.currentTenantId();
        tenantRepository.ensureExists(tenantId);
        List<UUID> toolIds = listOrEmpty(request.toolIds());
        List<UUID> knowledgeBaseIds = listOrEmpty(request.knowledgeBaseIds());
        validateModelId(tenantId, request.primaryModelId());
        validateModelId(tenantId, request.reasoningModelId());
        validateModelId(tenantId, request.embeddingModelId());
        toolIds.forEach(toolId -> validateToolId(tenantId, toolId));
        knowledgeBaseIds.forEach(knowledgeBaseId -> validateKnowledgeBaseId(tenantId, knowledgeBaseId));

        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setSystemPrompt(request.systemPrompt());
        entity.setModelName("configured-model");
        entity.setMaxTokens(4096);
        entity.setTools(AgentConfigEntity.emptyArray());
        entity.setKnowledgeBaseIds(toArray(knowledgeBaseIds));
        entity.setPrimaryModelId(request.primaryModelId());
        entity.setReasoningModelId(request.reasoningModelId());
        entity.setEmbeddingModelId(request.embeddingModelId());
        entity.setDefaultReasoningEffort(request.defaultReasoningEffort());

        AgentConfigEntity saved = agentRepository.save(entity);
        bindingRepository.deleteByTenantIdAndAgentConfigId(tenantId, saved.getId());
        toolIds.forEach(toolId -> bindingRepository.save(new AgentToolBindingEntity(saved.getId(), toolId, tenantId)));

        return toResponse(saved, toolIds);
    }

    @Transactional(readOnly = true)
    public List<AgentConfigResponse> list() {
        String tenantId = TenantContext.currentTenantId();
        return agentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(entity -> toResponse(
                        entity,
                        bindingRepository.findByTenantIdAndAgentConfigId(tenantId, entity.getId()).stream()
                                .map(AgentToolBindingEntity::getToolConfigId)
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        String tenantId = TenantContext.currentTenantId();
        bindingRepository.deleteByTenantIdAndAgentConfigId(tenantId, id);
        agentRepository.deleteByTenantIdAndId(tenantId, id);
    }

    private AgentConfigResponse toResponse(AgentConfigEntity entity, List<UUID> toolIds) {
        return new AgentConfigResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getSystemPrompt(),
                entity.getPrimaryModelId(),
                entity.getReasoningModelId(),
                entity.getEmbeddingModelId(),
                entity.getDefaultReasoningEffort(),
                listOrEmpty(toolIds),
                toList(entity.getKnowledgeBaseIds())
        );
    }

    private UUID[] toArray(List<UUID> values) {
        return listOrEmpty(values).toArray(UUID[]::new);
    }

    private void validateModelId(String tenantId, UUID modelId) {
        if (modelId != null && modelRepository.findByTenantIdAndId(tenantId, modelId).isEmpty()) {
            throw new IllegalArgumentException("Model config not found for tenant");
        }
    }

    private void validateToolId(String tenantId, UUID toolId) {
        if (toolRepository.findByTenantIdAndId(tenantId, toolId).isEmpty()) {
            throw new IllegalArgumentException("Tool config not found for tenant");
        }
    }

    private void validateKnowledgeBaseId(String tenantId, UUID knowledgeBaseId) {
        if (!knowledgeRepository.existsKnowledgeBase(tenantId, knowledgeBaseId)) {
            throw new IllegalArgumentException("Knowledge base not found for tenant");
        }
    }

    private List<UUID> toList(UUID[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return List.copyOf(Arrays.asList(values));
    }

    private List<UUID> listOrEmpty(List<UUID> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
