package com.agentscope.demo.toolconfig;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ToolRuntimeRegistrar {

    private final ToolRuntimeClient runtimeClient;

    public ToolRuntimeRegistrar(ToolRuntimeClient runtimeClient) {
        this.runtimeClient = runtimeClient;
    }

    public static ToolRuntimeRegistrar noop() {
        return new ToolRuntimeRegistrar(new HttpToolRuntimeClient()) {
            @Override
            public void registerConfiguredTools(Toolkit toolkit, List<ToolConfigEntity> tools) {
            }
        };
    }

    public void registerConfiguredTools(Toolkit toolkit, List<ToolConfigEntity> tools) {
        if (tools == null) {
            return;
        }
        for (ToolConfigEntity tool : tools) {
            if (!tool.isEnabled()) {
                continue;
            }
            if (tool.getToolType() == ToolType.JSON_RPC) {
                toolkit.registerAgentTool(new JsonRpcAgentTool(tool, runtimeClient));
            } else if (tool.getToolType() == ToolType.MCP) {
                registerMcp(toolkit, tool);
            }
        }
    }

    private void registerMcp(Toolkit toolkit, ToolConfigEntity tool) {
        String endpoint = tool.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        McpClientBuilder builder = McpClientBuilder.create(tool.getName())
                .timeout(Duration.ofMillis(tool.getTimeoutMs()));
        if (endpoint.startsWith("stdio://")) {
            builder.stdioTransport(endpoint.substring("stdio://".length()));
        } else if ("sse".equalsIgnoreCase(tool.getConfig().path("transport").asText(""))) {
            builder.sseTransport(endpoint);
        } else if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            builder.streamableHttpTransport(endpoint);
        } else {
            return;
        }
        if (tool.getAuthType() == ToolAuthType.BEARER && tool.getAuthRef() != null && !tool.getAuthRef().isBlank()) {
            builder.header("Authorization", "Bearer " + tool.getAuthRef());
        }
        toolkit.registerMcpClient(builder.buildSync()).block(Duration.ofMillis(tool.getTimeoutMs()));
    }
}
