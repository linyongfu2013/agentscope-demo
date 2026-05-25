package com.agentscope.demo.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.secret.MapSecretResolver;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAudioClientTest {

    @Test
    void transcribesAudioThroughOpenAiCompatibleEndpoint() throws IOException {
        CapturingAudioServer server = CapturingAudioServer.start("/v1/audio/transcriptions", "{\"text\":\"hello world\"}", "application/json");
        try {
            OpenAiCompatibleAudioClient client = new OpenAiCompatibleAudioClient(new MapSecretResolver(Map.of("OPENAI_API_KEY", "sk-test")));
            ModelConfigEntity model = model(ModelType.STT, "whisper-1", server.baseUrl(), "OPENAI_API_KEY");

            String transcript = client.transcribe(model, "recording.webm", "audio/webm", new byte[]{1, 2, 3});

            assertThat(transcript).isEqualTo("hello world");
            assertThat(server.authorization()).isEqualTo("Bearer sk-test");
            assertThat(server.body()).contains("name=\"model\"");
            assertThat(server.body()).contains("whisper-1");
            assertThat(server.body()).contains("recording.webm");
        } finally {
            server.stop();
        }
    }

    @Test
    void synthesizesSpeechThroughOpenAiCompatibleEndpoint() throws IOException {
        CapturingAudioServer server = CapturingAudioServer.start("/v1/audio/speech", "audio-bytes", "audio/mpeg");
        try {
            OpenAiCompatibleAudioClient client = new OpenAiCompatibleAudioClient(new MapSecretResolver(Map.of("OPENAI_API_KEY", "sk-test")));
            ModelConfigEntity model = model(ModelType.TTS, "gpt-4o-mini-tts", server.baseUrl(), "OPENAI_API_KEY");
            model.setExtraParams("{\"voice\":\"verse\",\"response_format\":\"mp3\"}");

            TextToSpeechClient.SynthesizedAudio audio = client.synthesize(model, "hi");

            assertThat(audio.bytes()).containsExactly("audio-bytes".getBytes(StandardCharsets.UTF_8));
            assertThat(audio.mimeType()).isEqualTo("audio/mpeg");
            assertThat(server.authorization()).isEqualTo("Bearer sk-test");
            assertThat(server.body()).contains("\"model\":\"gpt-4o-mini-tts\"");
            assertThat(server.body()).contains("\"voice\":\"verse\"");
            assertThat(server.body()).contains("\"input\":\"hi\"");
        } finally {
            server.stop();
        }
    }

    private static ModelConfigEntity model(ModelType type, String modelName, String baseUrl, String apiKeyRef) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId("tenant-a");
        entity.setName(type.name());
        entity.setProvider("openai");
        entity.setModelType(type);
        entity.setModelName(modelName);
        entity.setBaseUrl(baseUrl);
        entity.setApiKeyRef(apiKeyRef);
        entity.setEnabled(true);
        entity.setInputModalities("[]");
        entity.setOutputModalities("[]");
        entity.setExtraParams("{}");
        return entity;
    }

    private static final class CapturingAudioServer {
        private final HttpServer server;
        private final String path;
        private final String response;
        private final String contentType;
        private String body;
        private String authorization;

        private CapturingAudioServer(HttpServer server, String path, String response, String contentType) {
            this.server = server;
            this.path = path;
            this.response = response;
            this.contentType = contentType;
        }

        static CapturingAudioServer start(String path, String response, String contentType) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            CapturingAudioServer capturing = new CapturingAudioServer(server, path, response, contentType);
            server.createContext(path, exchange -> {
                capturing.authorization = exchange.getRequestHeaders().getFirst("Authorization");
                capturing.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return capturing;
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort() + "/v1";
        }

        String body() {
            return body;
        }

        String authorization() {
            return authorization;
        }

        void stop() {
            server.stop(0);
        }
    }
}
