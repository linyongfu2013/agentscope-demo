package com.agentscope.demo.chat;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationRepository implements ConversationRepository {

    private static final int TITLE_LIMIT = 80;

    private final JdbcClient jdbcClient;

    public JdbcConversationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void ensureConversation(String tenantId, UUID conversationId, UUID userId, String prompt) {
        if (conversationId == null) {
            return;
        }
        jdbcClient.sql("""
                        INSERT INTO conversations (id, tenant_id, user_id, title)
                        VALUES (:id, :tenantId, :userId, :title)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", conversationId)
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("title", titleFrom(prompt))
                .update();

        Optional<String> ownerTenant = jdbcClient.sql("""
                        SELECT tenant_id
                        FROM conversations
                        WHERE id = :id
                        """)
                .param("id", conversationId)
                .query(String.class)
                .optional();
        if (ownerTenant.isPresent() && !tenantId.equals(ownerTenant.get())) {
            throw new IllegalArgumentException("Conversation does not belong to tenant");
        }
    }

    private String titleFrom(String prompt) {
        String normalized = prompt == null ? "Untitled chat" : prompt.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "Untitled chat";
        }
        return normalized.length() <= TITLE_LIMIT ? normalized : normalized.substring(0, TITLE_LIMIT);
    }
}
