package com.agentscope.demo.voice;

import java.util.UUID;

public interface SpeechToTextClient {

    String transcribe(String filename, String mimeType, byte[] bytes, UUID modelConfigId);
}
