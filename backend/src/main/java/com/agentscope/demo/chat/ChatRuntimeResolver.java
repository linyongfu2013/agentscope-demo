package com.agentscope.demo.chat;

import com.agentscope.demo.agent.AgentConfigEntity;
import com.agentscope.demo.agent.AgentConfigRepository;
import com.agentscope.demo.agent.AgentToolBindingEntity;
import com.agentscope.demo.agent.AgentToolBindingRepository;
import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.model.ReasoningEffort;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.toolconfig.ToolConfigEntity;
import com.agentscope.demo.toolconfig.ToolConfigRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatRuntimeResolver {

    private final ModelConfigRepository modelRepository;
    private final AgentConfigRepository agentRepository;
    private final AgentToolBindingRepository bindingRepository;
    private final ToolConfigRepository toolRepository;

    public ChatRuntimeResolver(
            ModelConfigRepository modelRepository,
            AgentConfigRepository agentRepository,
            AgentToolBindingRepository bindingRepository,
            ToolConfigRepository toolRepository
    ) {
        this.modelRepository = modelRepository;
        this.agentRepository = agentRepository;
        this.bindingRepository = bindingRepository;
        this.toolRepository = toolRepository;
    }

    public ChatRuntimeSelection resolve(ChatRequest request) {
        String tenantId = TenantContext.currentTenantId();
        AgentConfigEntity agentEntity = request.agentConfigId() == null
                ? null
                : agentRepository.findByTenantIdAndId(tenantId, request.agentConfigId())
                        .orElseThrow(() -> new IllegalArgumentException("Selected agent not found"));
        List<ToolConfigEntity> selectedTools = resolveTools(tenantId, request, agentEntity);
        ModelConfigEntity model = resolveModel(tenantId, request, agentEntity);
        Optional<String> warning = Optional.empty();
        ReasoningEffort reasoningEffort = request.reasoningEffort();
        if (request.reasoningEffort() != null && model.getModelType() != ModelType.REASONING) {
            warning = Optional.of("reasoningEffort ignored because selected model is " + model.getModelType());
            reasoningEffort = null;
        }
        return new ChatRuntimeSelection(model, reasoningEffort, toAgentConfig(agentEntity, model, selectedTools), warning);
    }

    private ModelConfigEntity resolveModel(String tenantId, ChatRequest request, AgentConfigEntity agentEntity) {
        UUID modelId = request.modelId();
        if (modelId == null && agentEntity != null) {
            modelId = agentEntity.getPrimaryModelId();
        }
        return modelId == null
                ? defaultTextModel(tenantId)
                : modelRepository.findByTenantIdAndId(tenantId, modelId)
                        .orElseThrow(() -> new IllegalArgumentException("Selected model not found"));
    }

    private List<ToolConfigEntity> resolveTools(String tenantId, ChatRequest request, AgentConfigEntity agentEntity) {
        List<UUID> toolIds = request.toolIds().isEmpty() && agentEntity != null
                ? bindingRepository.findByTenantIdAndAgentConfigId(tenantId, agentEntity.getId()).stream()
                        .map(AgentToolBindingEntity::getToolConfigId)
                        .toList()
                : request.toolIds();
        List<ToolConfigEntity> tools = new ArrayList<>();
        for (UUID toolId : toolIds) {
            tools.add(toolRepository.findByTenantIdAndId(tenantId, toolId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected tool not found")));
        }
        return tools;
    }

    private AgentConfig toAgentConfig(AgentConfigEntity agentEntity, ModelConfigEntity model, List<ToolConfigEntity> tools) {
        AgentConfig defaults = AgentConfig.defaultAssistant();
        String name = agentEntity == null ? defaults.name() : agentEntity.getName();
        String systemPrompt = agentEntity == null ? defaults.systemPrompt() : agentEntity.getSystemPrompt();
        double temperature = model.getDefaultTemperature() == null
                ? defaults.temperature()
                : model.getDefaultTemperature().doubleValue();
        int maxTokens = model.getDefaultMaxTokens() == null ? defaults.maxTokens() : model.getDefaultMaxTokens();
        List<String> toolNames = tools.isEmpty()
                ? defaults.tools()
                : tools.stream().map(tool -> tool.getToolType().name().toLowerCase()).toList();
        List<UUID> knowledgeBaseIds = agentEntity == null
                ? defaults.knowledgeBaseIds()
                : List.of(agentEntity.getKnowledgeBaseIds());
        return new AgentConfig(
                agentEntity == null ? defaults.id() : agentEntity.getId(),
                name == null || name.isBlank() ? defaults.name() : name,
                systemPrompt == null || systemPrompt.isBlank() ? defaults.systemPrompt() : systemPrompt,
                model.getModelName(),
                temperature,
                maxTokens,
                toolNames,
                knowledgeBaseIds
        );
    }

    private ModelConfigEntity defaultTextModel(String tenantId) {
        return modelRepository.findByTenantIdAndModelTypeAndEnabledTrueOrderByCreatedAtDesc(tenantId, ModelType.TEXT)
                .stream()
                .findFirst()
                .orElseGet(() -> fallbackModel(tenantId));
    }

    private ModelConfigEntity fallbackModel(String tenantId) {
        Instant now = Instant.now();
        ModelConfigEntity model = new ModelConfigEntity();
        model.setId(UUID.randomUUID());
        model.setTenantId(tenantId);
        model.setName("Mock Chat");
        model.setProvider("mock");
        model.setModelType(ModelType.TEXT);
        model.setModelName("mock-chat");
        model.setDefaultTemperature(BigDecimal.valueOf(0.7));
        model.setDefaultMaxTokens(4096);
        model.setInputModalities("[]");
        model.setOutputModalities("[]");
        model.setExtraParams("{}");
        model.setEnabled(true);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return model;
    }
}
