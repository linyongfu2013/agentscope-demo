package com.agentscope.demo.toolconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpToolRuntimeClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void invokesJsonRpcEndpointWithConfiguredMethodAndParams() throws IOException {
        CapturingServer server = CapturingServer.start("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"ok\":true}}");
        try {
            ToolConfigEntity tool = tool(ToolType.JSON_RPC, server.url());
            tool.setConfig("{\"method\":\"search\",\"params\":{\"q\":\"agent\"}}");
            HttpToolRuntimeClient client = new HttpToolRuntimeClient();

            ToolInvocationResult result = client.test(tool);

            assertThat(result.success()).isTrue();
            assertThat(result.observation()).contains("\"ok\":true");
            assertThat(server.body()).contains("\"method\":\"search\"");
            assertThat(server.body()).contains("\"q\":\"agent\"");
        } finally {
            server.stop();
        }
    }

    @Test
    void listsMcpToolsOverStreamableHttpJsonRpc() throws IOException {
        CapturingServer server = CapturingServer.start(List.of(
                "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":[{\"name\":\"lookup\",\"description\":\"Lookup data\",\"inputSchema\":{\"type\":\"object\"}}]}}"
        ));
        try {
            ToolConfigEntity tool = tool(ToolType.MCP, server.url());
            HttpToolRuntimeClient client = new HttpToolRuntimeClient();

            ToolInvocationResult result = client.test(tool);

            assertThat(result.success()).isTrue();
            assertThat(result.observation()).contains("lookup");
            assertThat(server.bodies()).anySatisfy(body -> assertThat(body).contains("\"method\":\"initialize\""));
            assertThat(server.bodies()).anySatisfy(body -> assertThat(body).contains("\"method\":\"tools/list\""));
        } finally {
            server.stop();
        }
    }

    private static ToolConfigEntity tool(ToolType type, String endpoint) {
        ToolConfigEntity entity = new ToolConfigEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId("tenant-a");
        entity.setName("Remote Tool");
        entity.setToolType(type);
        entity.setEndpoint(endpoint);
        entity.setInputSchema("{}");
        entity.setConfig("{}");
        entity.setEnabled(true);
        return entity;
    }

    private static final class CapturingServer {
        private final HttpServer server;
        private final List<String> responses;
        private final java.util.List<String> bodies = new java.util.ArrayList<>();
        private int index;

        private CapturingServer(HttpServer server, List<String> responses) {
            this.server = server;
            this.responses = responses;
        }

        static CapturingServer start(String response) throws IOException {
            return start(List.of(response));
        }

        static CapturingServer start(List<String> responses) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            CapturingServer capturing = new CapturingServer(server, responses);
            server.createContext("/rpc", exchange -> {
                capturing.bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = capturing.responses.get(Math.min(capturing.index++, capturing.responses.size() - 1)).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return capturing;
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort() + "/rpc";
        }

        String body() {
            return bodies.getFirst();
        }

        List<String> bodies() {
            return bodies;
        }

        void stop() {
            server.stop(0);
        }
    }
}
