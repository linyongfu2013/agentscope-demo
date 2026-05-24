package com.agentscope.demo.agent;

import com.agentscope.demo.model.ReasoningEffort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_configs")
public class AgentConfigEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName = "configured-model";

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal temperature = new BigDecimal("0.700");

    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 4096;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode tools = OBJECT_MAPPER.createArrayNode();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "knowledge_base_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] knowledgeBaseIds = new UUID[0];

    @Column(name = "primary_model_id")
    private UUID primaryModelId;

    @Column(name = "reasoning_model_id")
    private UUID reasoningModelId;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_reasoning_effort", length = 32)
    private ReasoningEffort defaultReasoningEffort;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (modelName == null || modelName.isBlank()) {
            modelName = "configured-model";
        }
        if (temperature == null) {
            temperature = new BigDecimal("0.700");
        }
        if (maxTokens == null) {
            maxTokens = 4096;
        }
        if (tools == null) {
            tools = emptyArray();
        }
        if (knowledgeBaseIds == null) {
            knowledgeBaseIds = new UUID[0];
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static ArrayNode emptyArray() {
        return OBJECT_MAPPER.createArrayNode();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public JsonNode getTools() {
        return tools;
    }

    public void setTools(JsonNode tools) {
        this.tools = tools == null ? emptyArray() : tools;
    }

    public UUID[] getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public void setKnowledgeBaseIds(UUID[] knowledgeBaseIds) {
        this.knowledgeBaseIds = knowledgeBaseIds == null ? new UUID[0] : knowledgeBaseIds;
    }

    public UUID getPrimaryModelId() {
        return primaryModelId;
    }

    public void setPrimaryModelId(UUID primaryModelId) {
        this.primaryModelId = primaryModelId;
    }

    public UUID getReasoningModelId() {
        return reasoningModelId;
    }

    public void setReasoningModelId(UUID reasoningModelId) {
        this.reasoningModelId = reasoningModelId;
    }

    public UUID getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(UUID embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public ReasoningEffort getDefaultReasoningEffort() {
        return defaultReasoningEffort;
    }

    public void setDefaultReasoningEffort(ReasoningEffort defaultReasoningEffort) {
        this.defaultReasoningEffort = defaultReasoningEffort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
