package com.agentscope.demo.chat;

import java.util.UUID;

public interface TaskRunRepository {

    UUID create(String tenantId, ChatRequest request, Intent intent);

    void markSuccess(String tenantId, UUID taskRunId, String finalAnswer);

    void markFailed(String tenantId, UUID taskRunId, String errorMessage);
}
