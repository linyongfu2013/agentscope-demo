package com.agentscope.demo.voice;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.secret.SecretResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleAudioClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SecretResolver secretResolver;
    private final HttpClient httpClient;

    public OpenAiCompatibleAudioClient(SecretResolver secretResolver) {
        this(secretResolver, HttpClient.newHttpClient());
    }

    OpenAiCompatibleAudioClient(SecretResolver secretResolver, HttpClient httpClient) {
        this.secretResolver = secretResolver;
        this.httpClient = httpClient;
    }

    public String transcribe(ModelConfigEntity model, String filename, String mimeType, byte[] bytes) {
        try {
            String boundary = "agentscope-" + UUID.randomUUID();
            byte[] body = multipartTranscriptionBody(boundary, model.getModelName(), filename, mimeType, bytes);
            HttpRequest request = HttpRequest.newBuilder(audioUri(model, "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey(model))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("STT provider returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            return json.path("text").asText("");
        } catch (IOException e) {
            throw new IllegalStateException("failed to call STT provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted calling STT provider", e);
        }
    }

    public TextToSpeechClient.SynthesizedAudio synthesize(ModelConfigEntity model, String text) {
        try {
            JsonNode extra = model.getExtraParams();
            String voice = extra.path("voice").asText("alloy");
            String responseFormat = extra.path("response_format").asText("mp3");
            String payload = OBJECT_MAPPER.writeValueAsString(new SpeechRequest(model.getModelName(), text, voice, responseFormat));
            HttpRequest request = HttpRequest.newBuilder(audioUri(model, "/audio/speech"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey(model))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("TTS provider returned HTTP " + response.statusCode() + ": "
                        + new String(response.body(), StandardCharsets.UTF_8));
            }
            String mimeType = response.headers().firstValue("Content-Type").orElse(mimeTypeFor(responseFormat));
            return new TextToSpeechClient.SynthesizedAudio(response.body(), mimeType, null);
        } catch (IOException e) {
            throw new IllegalStateException("failed to call TTS provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted calling TTS provider", e);
        }
    }

    private URI audioUri(ModelConfigEntity model, String path) {
        String baseUrl = model.getBaseUrl() == null || model.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : model.getBaseUrl();
        return URI.create(baseUrl.replaceAll("/+$", "") + path);
    }

    private String apiKey(ModelConfigEntity model) {
        return secretResolver.resolve(model.getApiKeyRef())
                .orElseThrow(() -> new IllegalStateException("missing API key for " + model.getName()));
    }

    private byte[] multipartTranscriptionBody(String boundary, String modelName, String filename, String mimeType, byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "model", null, null, modelName.getBytes(StandardCharsets.UTF_8));
        writePart(out, boundary, "file", filename == null || filename.isBlank() ? "audio.webm" : filename,
                mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType, bytes);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private void writePart(ByteArrayOutputStream out, String boundary, String name, String filename, String contentType, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) {
            disposition += "; filename=\"" + filename.replace("\"", "") + "\"";
        }
        out.write((disposition + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        if (contentType != null) {
            out.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
    }

    private String mimeTypeFor(String responseFormat) {
        return switch (responseFormat) {
            case "wav" -> "audio/wav";
            case "opus" -> "audio/opus";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            default -> "audio/mpeg";
        };
    }

    private record SpeechRequest(String model, String input, String voice, String response_format) {
    }
}
