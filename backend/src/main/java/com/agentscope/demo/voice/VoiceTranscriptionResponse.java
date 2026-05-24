package com.agentscope.demo.voice;

import java.util.UUID;

public record VoiceTranscriptionResponse(UUID audioArtifactId, String transcript) {
}
