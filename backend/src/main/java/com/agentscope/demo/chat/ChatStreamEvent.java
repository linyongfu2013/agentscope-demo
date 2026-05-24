package com.agentscope.demo.chat;

import java.util.Map;

public record ChatStreamEvent(
        String id,
        String runId,
        long sequence,
        String type,
        boolean collapsed,
        Map<String, Object> payload
) {
}
