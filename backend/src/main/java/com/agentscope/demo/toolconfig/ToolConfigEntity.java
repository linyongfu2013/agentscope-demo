package com.agentscope.demo.toolconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tool_configs")
public class ToolConfigEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", nullable = false, length = 32)
    private ToolType toolType;

    @Column(columnDefinition = "text")
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", length = 32)
    private ToolAuthType authType = ToolAuthType.NONE;

    @Column(name = "auth_ref", columnDefinition = "text")
    private String authRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputSchema = emptyObject();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode config = emptyObject();

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 30000;

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
        if (authType == null) {
            authType = ToolAuthType.NONE;
        }
        if (inputSchema == null) {
            inputSchema = emptyObject();
        }
        if (config == null) {
            config = emptyObject();
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (authType == null) {
            authType = ToolAuthType.NONE;
        }
        if (inputSchema == null) {
            inputSchema = emptyObject();
        }
        if (config == null) {
            config = emptyObject();
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ToolType getToolType() {
        return toolType;
    }

    public void setToolType(ToolType toolType) {
        this.toolType = toolType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public ToolAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(ToolAuthType authType) {
        this.authType = authType == null ? ToolAuthType.NONE : authType;
    }

    public String getAuthRef() {
        return authRef;
    }

    public void setAuthRef(String authRef) {
        this.authRef = authRef;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonNode inputSchema) {
        this.inputSchema = inputSchema == null ? emptyObject() : inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = parseJson(inputSchema, emptyObject());
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config == null ? emptyObject() : config;
    }

    public void setConfig(String config) {
        this.config = parseJson(config, emptyObject());
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs <= 0 ? 30000 : timeoutMs;
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
