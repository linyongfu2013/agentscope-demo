package com.agentscope.demo.toolconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HttpToolRuntimeClient implements ToolRuntimeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpToolRuntimeClient() {
        this(HttpClient.newHttpClient());
    }

    HttpToolRuntimeClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ToolInvocationResult test(ToolConfigEntity entity) {
        if (entity.getToolType() == ToolType.JSON_RPC) {
            JsonNode params = entity.getConfig().path("params");
            return invokeJsonRpc(entity, configuredMethod(entity), params.isMissingNode() ? OBJECT_MAPPER.createObjectNode() : params);
        }
        if (entity.getToolType() == ToolType.MCP) {
            return testMcp(entity);
        }
        return new ToolInvocationResult(true, "built-in tool " + entity.getName() + " is available");
    }

    @Override
    public ToolInvocationResult invoke(ToolConfigEntity entity, Map<String, Object> input) {
        if (entity.getToolType() != ToolType.JSON_RPC) {
            return test(entity);
        }
        JsonNode params = OBJECT_MAPPER.valueToTree(input == null ? Map.of() : input);
        return invokeJsonRpc(entity, configuredMethod(entity), params);
    }

    private ToolInvocationResult testMcp(ToolConfigEntity entity) {
        if (!isHttpEndpoint(entity.getEndpoint())) {
            return new ToolInvocationResult(false, "MCP test supports HTTP Streamable transport endpoints; got " + entity.getEndpoint());
        }
        ToolInvocationResult initialized = sendMcp(entity, "1", "initialize", OBJECT_MAPPER.createObjectNode()
                .put("protocolVersion", entity.getConfig().path("protocolVersion").asText("2025-11-25"))
                .set("capabilities", OBJECT_MAPPER.createObjectNode()));
        if (!initialized.success()) {
            return initialized;
        }
        JsonNode toolName = entity.getConfig().path("toolName");
        if (!toolName.isMissingNode() && !toolName.asText().isBlank()) {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("name", toolName.asText());
            params.set("arguments", entity.getConfig().path("arguments").isMissingNode()
                    ? OBJECT_MAPPER.createObjectNode()
                    : entity.getConfig().path("arguments"));
            return sendMcp(entity, "2", "tools/call", params);
        }
        return sendMcp(entity, "2", "tools/list", OBJECT_MAPPER.createObjectNode());
    }

    private ToolInvocationResult sendMcp(ToolConfigEntity entity, String id, String method, JsonNode params) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        return postJson(entity, request);
    }

    private ToolInvocationResult invokeJsonRpc(ToolConfigEntity entity, String method, JsonNode params) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", method);
        request.set("params", params);
        return postJson(entity, request);
    }

    private ToolInvocationResult postJson(ToolConfigEntity entity, JsonNode payload) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(entity.getEndpoint()))
                    .timeout(Duration.ofMillis(entity.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)));
            applyAuth(entity, builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ToolInvocationResult(false, "HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode body = parseJsonOrSse(response.body());
            if (body.hasNonNull("error")) {
                return new ToolInvocationResult(false, body.get("error").toString());
            }
            JsonNode result = body.has("result") ? body.get("result") : body;
            return new ToolInvocationResult(true, OBJECT_MAPPER.writeValueAsString(result));
        } catch (IOException e) {
            return new ToolInvocationResult(false, "I/O error invoking tool: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolInvocationResult(false, "Interrupted invoking tool");
        } catch (RuntimeException e) {
            return new ToolInvocationResult(false, "Failed invoking tool: " + e.getMessage());
        }
    }

    private String configuredMethod(ToolConfigEntity entity) {
        String method = entity.getConfig().path("method").asText("");
        return method.isBlank() ? entity.getName() : method;
    }

    private JsonNode parseJsonOrSse(String body) throws IOException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("data:")) {
            for (String line : trimmed.split("\\R")) {
                if (line.startsWith("data:")) {
                    return OBJECT_MAPPER.readTree(line.substring("data:".length()).trim());
                }
            }
        }
        return OBJECT_MAPPER.readTree(trimmed);
    }

    private boolean isHttpEndpoint(String endpoint) {
        return endpoint != null && (endpoint.startsWith("http://") || endpoint.startsWith("https://"));
    }

    private void applyAuth(ToolConfigEntity entity, HttpRequest.Builder builder) {
        if (entity.getAuthType() == ToolAuthType.BEARER && entity.getAuthRef() != null && !entity.getAuthRef().isBlank()) {
            builder.header("Authorization", "Bearer " + entity.getAuthRef());
        } else if (entity.getAuthType() == ToolAuthType.API_KEY && entity.getAuthRef() != null && !entity.getAuthRef().isBlank()) {
            String header = entity.getConfig().path("apiKeyHeader").asText("X-API-Key");
            builder.header(header, entity.getAuthRef());
        }
    }
}
