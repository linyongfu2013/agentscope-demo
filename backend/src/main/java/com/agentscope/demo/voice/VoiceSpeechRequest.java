package com.agentscope.demo.voice;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record VoiceSpeechRequest(@NotBlank String text, UUID ttsModelId) {
}
