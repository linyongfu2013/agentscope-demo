package com.agentscope.demo.voice;

import java.util.UUID;

public record VoiceSpeechResponse(UUID audioArtifactId, String audioUrl, String mimeType) {
}
