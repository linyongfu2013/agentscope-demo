package com.agentscope.demo.chat;

import java.util.UUID;

public interface ConversationRepository {

    void ensureConversation(String tenantId, UUID conversationId, UUID userId, String prompt);
}
