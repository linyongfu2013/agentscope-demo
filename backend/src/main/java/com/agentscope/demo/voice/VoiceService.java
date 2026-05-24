package com.agentscope.demo.voice;

import com.agentscope.demo.storage.FileStorage;
import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.model.ModelType;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.tenant.TenantRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VoiceService {

    private final AudioArtifactRepository repository;
    private final FileStorage fileStorage;
    private final TenantRepository tenantRepository;
    private final ModelConfigRepository modelRepository;
    private final SpeechToTextClient speechToTextClient;
    private final TextToSpeechClient textToSpeechClient;

    public VoiceService(
            AudioArtifactRepository repository,
            FileStorage fileStorage,
            TenantRepository tenantRepository,
            ModelConfigRepository modelRepository,
            SpeechToTextClient speechToTextClient,
            TextToSpeechClient textToSpeechClient
    ) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.tenantRepository = tenantRepository;
        this.modelRepository = modelRepository;
        this.speechToTextClient = speechToTextClient;
        this.textToSpeechClient = textToSpeechClient;
    }

    @Transactional
    public VoiceTranscriptionResponse transcribe(String filename, String mimeType, byte[] bytes, UUID sttModelId) {
        String tenantId = TenantContext.currentTenantId();
        tenantRepository.ensureExists(tenantId);
        validateVoiceModel(tenantId, sttModelId, ModelType.STT);
        String transcript = speechToTextClient.transcribe(filename, mimeType, bytes, sttModelId);
        String storageKey = writeBytes(tenantId, filename, bytes);

        AudioArtifactEntity entity = new AudioArtifactEntity();
        entity.setTenantId(tenantId);
        entity.setDirection(AudioDirection.INPUT);
        entity.setStorageKey(storageKey);
        entity.setMimeType(defaultMimeType(mimeType));
        entity.setTranscript(transcript);
        entity.setModelConfigId(sttModelId);

        AudioArtifactEntity saved = repository.save(entity);
        return new VoiceTranscriptionResponse(saved.getId(), saved.getTranscript());
    }

    @Transactional
    public VoiceSpeechResponse speech(VoiceSpeechRequest request) {
        String tenantId = TenantContext.currentTenantId();
        tenantRepository.ensureExists(tenantId);
        validateVoiceModel(tenantId, request.ttsModelId(), ModelType.TTS);
        TextToSpeechClient.SynthesizedAudio audio = textToSpeechClient.synthesize(request.text(), request.ttsModelId());
        String storageKey = writeBytes(tenantId, "speech.txt", audio.bytes());

        AudioArtifactEntity entity = new AudioArtifactEntity();
        entity.setTenantId(tenantId);
        entity.setDirection(AudioDirection.OUTPUT);
        entity.setStorageKey(storageKey);
        entity.setMimeType(defaultMimeType(audio.mimeType()));
        entity.setDurationMs(audio.durationMs());
        entity.setTranscript(request.text());
        entity.setModelConfigId(request.ttsModelId());

        AudioArtifactEntity saved = repository.save(entity);
        return new VoiceSpeechResponse(
                saved.getId(),
                "/api/audio-artifacts/" + saved.getId() + "/content",
                saved.getMimeType()
        );
    }

    @Transactional(readOnly = true)
    public AudioArtifactResponse findArtifact(UUID id) {
        return toResponse(findTenantArtifact(id));
    }

    @Transactional(readOnly = true)
    public AudioContent readContent(UUID id) {
        AudioArtifactEntity entity = findTenantArtifact(id);
        try {
            return new AudioContent(fileStorage.get(entity.getStorageKey()).readAllBytes(), entity.getMimeType());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read audio artifact content", e);
        }
    }

    private AudioArtifactEntity findTenantArtifact(UUID id) {
        String tenantId = TenantContext.currentTenantId();
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "audio artifact not found"));
    }

    private void validateVoiceModel(String tenantId, UUID modelId, ModelType expectedType) {
        if (modelId == null) {
            return;
        }
        ModelConfigEntity model = modelRepository.findByTenantIdAndId(tenantId, modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "voice model not found"));
        if (model.getModelType() != expectedType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "voice model must be " + expectedType);
        }
    }

    private String writeBytes(String tenantId, String filename, byte[] bytes) {
        String storageKey = "voice/" + tenantId + "/" + UUID.randomUUID() + "-" + safeFilename(filename);
        try {
            return fileStorage.put(storageKey, new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to store audio artifact", e);
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "audio";
        }
        return filename.replace('\\', '_').replace('/', '_');
    }

    private String defaultMimeType(String mimeType) {
        return mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
    }

    private AudioArtifactResponse toResponse(AudioArtifactEntity entity) {
        return new AudioArtifactResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getConversationId(),
                entity.getMessageId(),
                entity.getDirection(),
                entity.getStorageKey(),
                entity.getMimeType(),
                entity.getDurationMs(),
                entity.getTranscript(),
                entity.getModelConfigId(),
                entity.getCreatedAt()
        );
    }

    public record AudioContent(byte[] bytes, String mimeType) {
    }
}
