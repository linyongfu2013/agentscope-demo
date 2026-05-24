package com.agentscope.demo.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.storage.FileStorage;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class VoiceServiceTest {

    @Test
    void storesRecordingAndReturnsMockTranscript() {
        AudioArtifactRepository repository = Mockito.mock(AudioArtifactRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        MemoryStorage storage = new MemoryStorage();
        VoiceService service = new VoiceService(
                repository,
                storage,
                tenantRepository,
                modelRepository,
                new MockSpeechToTextClient(),
                new MockTextToSpeechClient()
        );
        UUID savedId = UUID.randomUUID();

        when(repository.save(any(AudioArtifactEntity.class))).thenAnswer(invocation -> savedEntity(invocation.getArgument(0), savedId));

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceTranscriptionResponse response = service.transcribe(
                    "recording.webm",
                    "audio/webm",
                    new byte[]{1, 2, 3},
                    null
            );

            assertThat(response.audioArtifactId()).isEqualTo(savedId);
            assertThat(response.transcript()).isEqualTo("mock transcript from recording.webm");
        });

        verify(tenantRepository).ensureExists("tenant-a");
        verify(repository).save(Mockito.argThat(entity ->
                entity.getDirection() == AudioDirection.INPUT
                        && entity.getTenantId().equals("tenant-a")
                        && entity.getStorageKey().contains("recording.webm")
                        && entity.getMimeType().equals("audio/webm")
                        && entity.getTranscript().equals("mock transcript from recording.webm")
        ));
        assertThat(storage.bytesByKey).hasSize(1);
        assertThat(storage.bytesByKey.values().iterator().next()).containsExactly(1, 2, 3);
    }

    @Test
    void storesSpeechOutputAndReturnsContentUrl() {
        AudioArtifactRepository repository = Mockito.mock(AudioArtifactRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        MemoryStorage storage = new MemoryStorage();
        VoiceService service = new VoiceService(
                repository,
                storage,
                tenantRepository,
                modelRepository,
                new MockSpeechToTextClient(),
                new MockTextToSpeechClient()
        );
        UUID savedId = UUID.randomUUID();

        when(repository.save(any(AudioArtifactEntity.class))).thenAnswer(invocation -> savedEntity(invocation.getArgument(0), savedId));

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceSpeechResponse response = service.speech(new VoiceSpeechRequest("hello", null));

            assertThat(response.audioArtifactId()).isEqualTo(savedId);
            assertThat(response.audioUrl()).isEqualTo("/api/audio-artifacts/" + savedId + "/content");
            assertThat(response.mimeType()).isEqualTo("audio/plain");
        });

        verify(tenantRepository).ensureExists("tenant-a");
        verify(repository).save(Mockito.argThat(entity ->
                entity.getDirection() == AudioDirection.OUTPUT
                        && entity.getTenantId().equals("tenant-a")
                        && entity.getMimeType().equals("audio/plain")
                        && entity.getTranscript().equals("hello")
        ));
        assertThat(storage.bytesByKey).hasSize(1);
        assertThat(storage.bytesByKey.values().iterator().next()).containsExactly("mock audio: hello".getBytes());
    }

    @Test
    void readsTenantScopedContentFromStoredArtifact() {
        AudioArtifactRepository repository = Mockito.mock(AudioArtifactRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        MemoryStorage storage = new MemoryStorage();
        VoiceService service = new VoiceService(
                repository,
                storage,
                tenantRepository,
                modelRepository,
                new MockSpeechToTextClient(),
                new MockTextToSpeechClient()
        );
        UUID id = UUID.randomUUID();
        AudioArtifactEntity entity = new AudioArtifactEntity();
        entity.setId(id);
        entity.setTenantId("tenant-a");
        entity.setDirection(AudioDirection.OUTPUT);
        entity.setStorageKey("voice/tenant-a/file.txt");
        entity.setMimeType("audio/plain");
        entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        storage.bytesByKey.put(entity.getStorageKey(), "audio bytes".getBytes());
        when(repository.findByTenantIdAndId("tenant-a", id)).thenReturn(Optional.of(entity));

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceService.AudioContent content = service.readContent(id);

            assertThat(content.mimeType()).isEqualTo("audio/plain");
            assertThat(content.bytes()).containsExactly("audio bytes".getBytes());
        });

        verify(repository).findByTenantIdAndId("tenant-a", id);
    }

    @Test
    void rejectsSttModelFromAnotherTenantOrWrongType() {
        AudioArtifactRepository repository = Mockito.mock(AudioArtifactRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        VoiceService service = new VoiceService(
                repository,
                new MemoryStorage(),
                tenantRepository,
                modelRepository,
                new MockSpeechToTextClient(),
                new MockTextToSpeechClient()
        );
        UUID modelId = UUID.randomUUID();
        ModelConfigEntity textModel = model("tenant-a", modelId, ModelType.TEXT);

        when(modelRepository.findByTenantIdAndId("tenant-a", modelId)).thenReturn(Optional.of(textModel));

        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.transcribe("recording.webm", "audio/webm", new byte[]{1}, modelId))
                        .isInstanceOf(ResponseStatusException.class)
                        .hasMessageContaining("voice model must be STT")
        );

        verify(modelRepository).findByTenantIdAndId("tenant-a", modelId);
    }

    @Test
    void acceptsTenantScopedTtsModel() {
        AudioArtifactRepository repository = Mockito.mock(AudioArtifactRepository.class);
        TenantRepository tenantRepository = Mockito.mock(TenantRepository.class);
        ModelConfigRepository modelRepository = Mockito.mock(ModelConfigRepository.class);
        VoiceService service = new VoiceService(
                repository,
                new MemoryStorage(),
                tenantRepository,
                modelRepository,
                new MockSpeechToTextClient(),
                new MockTextToSpeechClient()
        );
        UUID modelId = UUID.randomUUID();
        UUID savedId = UUID.randomUUID();

        when(modelRepository.findByTenantIdAndId("tenant-a", modelId)).thenReturn(Optional.of(model("tenant-a", modelId, ModelType.TTS)));
        when(repository.save(any(AudioArtifactEntity.class))).thenAnswer(invocation -> savedEntity(invocation.getArgument(0), savedId));

        TenantContext.runWithTenant("tenant-a", () -> {
            VoiceSpeechResponse response = service.speech(new VoiceSpeechRequest("hello", modelId));

            assertThat(response.audioArtifactId()).isEqualTo(savedId);
        });

        verify(modelRepository).findByTenantIdAndId("tenant-a", modelId);
        verify(repository).save(Mockito.argThat(entity -> modelId.equals(entity.getModelConfigId())));
    }

    private static AudioArtifactEntity savedEntity(AudioArtifactEntity entity, UUID savedId) {
        entity.setId(savedId);
        entity.setCreatedAt(Instant.parse("2026-05-24T10:00:00Z"));
        return entity;
    }

    private static ModelConfigEntity model(String tenantId, UUID id, ModelType modelType) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setName(modelType.name() + " model");
        entity.setProvider("mock");
        entity.setModelType(modelType);
        entity.setModelName("mock-" + modelType.name().toLowerCase());
        entity.setEnabled(true);
        return entity;
    }

    private static final class MemoryStorage implements FileStorage {
        private final Map<String, byte[]> bytesByKey = new HashMap<>();

        @Override
        public String put(String key, InputStream inputStream) throws IOException {
            bytesByKey.put(key, inputStream.readAllBytes());
            return key;
        }

        @Override
        public InputStream get(String key) {
            return new ByteArrayInputStream(bytesByKey.get(key));
        }
    }
}
