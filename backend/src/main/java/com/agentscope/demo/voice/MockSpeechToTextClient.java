package com.agentscope.demo.voice;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockSpeechToTextClient implements SpeechToTextClient {

    @Override
    public String transcribe(String filename, String mimeType, byte[] bytes, UUID modelConfigId) {
        return "mock transcript from " + filename;
    }
}
