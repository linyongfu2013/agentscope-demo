package com.agentscope.demo.voice;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockTextToSpeechClient implements TextToSpeechClient {

    @Override
    public SynthesizedAudio synthesize(String text, UUID modelConfigId) {
        return new SynthesizedAudio(("mock audio: " + text).getBytes(StandardCharsets.UTF_8), "audio/plain", null);
    }
}
