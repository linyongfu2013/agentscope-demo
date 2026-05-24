package com.agentscope.demo.voice;

import java.util.UUID;

public interface TextToSpeechClient {

    SynthesizedAudio synthesize(String text, UUID modelConfigId);

    record SynthesizedAudio(byte[] bytes, String mimeType, Integer durationMs) {
    }
}
