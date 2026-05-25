package com.agentscope.demo.toolconfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import reactor.core.publisher.Mono;

public class JsonRpcAgentTool implements AgentTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ToolConfigEntity entity;
    private final ToolRuntimeClient runtimeClient;

    public JsonRpcAgentTool(ToolConfigEntity entity, ToolRuntimeClient runtimeClient) {
        this.entity = entity;
        this.runtimeClient = runtimeClient;
    }

    @Override
    public String getName() {
        return normalizeToolName(entity.getName());
    }

    @Override
    public String getDescription() {
        return entity.getDescription() == null || entity.getDescription().isBlank()
                ? "Configured JSON-RPC tool"
                : entity.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        if (entity.getInputSchema() == null || entity.getInputSchema().isEmpty()) {
            return Map.of("type", "object");
        }
        return OBJECT_MAPPER.convertValue(entity.getInputSchema(), MAP_TYPE);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromSupplier(() -> {
            ToolInvocationResult result = runtimeClient.invoke(entity, param.getInput());
            return result.success()
                    ? ToolResultBlock.text(result.observation())
                    : ToolResultBlock.error(result.observation());
        });
    }

    static String normalizeToolName(String name) {
        String normalized = name == null ? "json_rpc_tool" : name.toLowerCase().replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "json_rpc_tool" : normalized;
    }
}
