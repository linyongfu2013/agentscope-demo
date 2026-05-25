package com.agentscope.demo.voice;

import com.agentscope.demo.model.ModelConfigEntity;
import com.agentscope.demo.model.ModelConfigRepository;
import com.agentscope.demo.tenant.TenantContext;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ConfiguredTextToSpeechClient implements TextToSpeechClient {

    private final ModelConfigRepository modelRepository;
    private final OpenAiCompatibleAudioClient audioClient;
    private final MockTextToSpeechClient mockClient;

    public ConfiguredTextToSpeechClient(
            ModelConfigRepository modelRepository,
            OpenAiCompatibleAudioClient audioClient,
            MockTextToSpeechClient mockClient
    ) {
        this.modelRepository = modelRepository;
        this.audioClient = audioClient;
        this.mockClient = mockClient;
    }

    @Override
    public SynthesizedAudio synthesize(String text, UUID modelConfigId) {
        if (modelConfigId == null) {
            return mockClient.synthesize(text, null);
        }
        ModelConfigEntity model = modelRepository.findByTenantIdAndId(TenantContext.currentTenantId(), modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("voice model not found"));
        if ("mock".equalsIgnoreCase(model.getProvider())) {
            return mockClient.synthesize(text, modelConfigId);
        }
        return audioClient.synthesize(model, text);
    }
}
