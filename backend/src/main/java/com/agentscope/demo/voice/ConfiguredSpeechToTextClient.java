package com.agentscope.demo.voice;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.tenant.TenantContext;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ConfiguredSpeechToTextClient implements SpeechToTextClient {

    private final ModelConfigRepository modelRepository;
    private final OpenAiCompatibleAudioClient audioClient;
    private final MockSpeechToTextClient mockClient;

    public ConfiguredSpeechToTextClient(
            ModelConfigRepository modelRepository,
            OpenAiCompatibleAudioClient audioClient,
            MockSpeechToTextClient mockClient
    ) {
        this.modelRepository = modelRepository;
        this.audioClient = audioClient;
        this.mockClient = mockClient;
    }

    @Override
    public String transcribe(String filename, String mimeType, byte[] bytes, UUID modelConfigId) {
        if (modelConfigId == null) {
            return mockClient.transcribe(filename, mimeType, bytes, null);
        }
        ModelConfigEntity model = modelRepository.findByTenantIdAndId(TenantContext.currentTenantId(), modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("voice model not found"));
        if ("mock".equalsIgnoreCase(model.getProvider())) {
            return mockClient.transcribe(filename, mimeType, bytes, modelConfigId);
        }
        return audioClient.transcribe(model, filename, mimeType, bytes);
    }
}
