package com.agentscope.demo.chat;

import java.util.UUID;

public interface TaskEventRepository {

    void append(String tenantId, UUID taskRunId, ChatStreamEvent event);
}
