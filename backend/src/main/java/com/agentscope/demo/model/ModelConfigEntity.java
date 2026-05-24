package com.agentscope.demo.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
@Table(name = "model_configs")
public class ModelConfigEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 32)
    private ModelType modelType;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "base_url", columnDefinition = "text")
    private String baseUrl;

    @Column(name = "api_key_ref", columnDefinition = "text")
    private String apiKeyRef;

    @Column(name = "default_temperature", precision = 4, scale = 3)
    private BigDecimal defaultTemperature;

    @Column(name = "default_max_tokens")
    private Integer defaultMaxTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_reasoning_effort", length = 32)
    private ReasoningEffort defaultReasoningEffort;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_modalities", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputModalities = OBJECT_MAPPER.createArrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_modalities", nullable = false, columnDefinition = "jsonb")
    private JsonNode outputModalities = OBJECT_MAPPER.createArrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_params", nullable = false, columnDefinition = "jsonb")
    private JsonNode extraParams = OBJECT_MAPPER.createObjectNode();

    @Column(nullable = false)
    private boolean enabled = true;

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
        if (inputModalities == null) {
            inputModalities = emptyArray();
        }
        if (outputModalities == null) {
            outputModalities = emptyArray();
        }
        if (extraParams == null) {
            extraParams = emptyObject();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public void setModelType(ModelType modelType) {
        this.modelType = modelType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyRef() {
        return apiKeyRef;
    }

    public void setApiKeyRef(String apiKeyRef) {
        this.apiKeyRef = apiKeyRef;
    }

    public BigDecimal getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(BigDecimal defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public Integer getDefaultMaxTokens() {
        return defaultMaxTokens;
    }

    public void setDefaultMaxTokens(Integer defaultMaxTokens) {
        this.defaultMaxTokens = defaultMaxTokens;
    }

    public ReasoningEffort getDefaultReasoningEffort() {
        return defaultReasoningEffort;
    }

    public void setDefaultReasoningEffort(ReasoningEffort defaultReasoningEffort) {
        this.defaultReasoningEffort = defaultReasoningEffort;
    }

    public Integer getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(Integer embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public JsonNode getInputModalities() {
        return inputModalities;
    }

    public void setInputModalities(JsonNode inputModalities) {
        this.inputModalities = inputModalities == null ? emptyArray() : inputModalities;
    }

    public void setInputModalities(String inputModalities) {
        this.inputModalities = parseJson(inputModalities, emptyArray());
    }

    public JsonNode getOutputModalities() {
        return outputModalities;
    }

    public void setOutputModalities(JsonNode outputModalities) {
        this.outputModalities = outputModalities == null ? emptyArray() : outputModalities;
    }

    public void setOutputModalities(String outputModalities) {
        this.outputModalities = parseJson(outputModalities, emptyArray());
    }

    public JsonNode getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(JsonNode extraParams) {
        this.extraParams = extraParams == null ? emptyObject() : extraParams;
    }

    public void setExtraParams(String extraParams) {
        this.extraParams = parseJson(extraParams, emptyObject());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    static ArrayNode emptyArray() {
        return OBJECT_MAPPER.createArrayNode();
    }

    static ObjectNode emptyObject() {
        return OBJECT_MAPPER.createObjectNode();
    }

    static JsonNode parseJson(String json, JsonNode fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON", e);
        }
    }
}
