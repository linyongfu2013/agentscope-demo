package com.agentscope.demo.chat;

import com.agentscope.demo.tenant.TenantRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskRunRepository implements TaskRunRepository {

    private final JdbcClient jdbcClient;
    private final TenantRepository tenantRepository;
    private final ConversationRepository conversationRepository;

    public JdbcTaskRunRepository(JdbcClient jdbcClient, TenantRepository tenantRepository, ConversationRepository conversationRepository) {
        this.jdbcClient = jdbcClient;
        this.tenantRepository = tenantRepository;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public UUID create(String tenantId, ChatRequest request, Intent intent) {
        tenantRepository.ensureExists(tenantId);
        conversationRepository.ensureConversation(tenantId, request.conversationId(), request.userId(), request.prompt());
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO agent_task_runs
                            (id, tenant_id, conversation_id, agent_config_id, user_prompt, intent, status)
                        VALUES
                            (:id, :tenantId, :conversationId, :agentConfigId, :prompt, :intent, CAST(:status AS task_run_status))
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("conversationId", request.conversationId())
                .param("agentConfigId", request.agentConfigId())
                .param("prompt", request.prompt())
                .param("intent", intent.name())
                .param("status", "RUNNING")
                .update();
        return id;
    }

    @Override
    public void markSuccess(String tenantId, UUID taskRunId, String finalAnswer) {
        jdbcClient.sql("""
                        UPDATE agent_task_runs
                        SET status = CAST('SUCCESS' AS task_run_status),
                            final_answer = :finalAnswer,
                            completed_at = now()
                        WHERE tenant_id = :tenantId AND id = :id
                        """)
                .param("tenantId", tenantId)
                .param("id", taskRunId)
                .param("finalAnswer", finalAnswer)
                .update();
    }

    @Override
    public void markFailed(String tenantId, UUID taskRunId, String errorMessage) {
        jdbcClient.sql("""
                        UPDATE agent_task_runs
                        SET status = CAST('FAILED' AS task_run_status),
                            error_message = :errorMessage,
                            completed_at = now()
                        WHERE tenant_id = :tenantId AND id = :id
                        """)
                .param("tenantId", tenantId)
                .param("id", taskRunId)
                .param("errorMessage", errorMessage)
                .update();
    }
}
