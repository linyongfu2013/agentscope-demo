package com.agentscope.demo.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskEventRepository implements TaskEventRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcTaskEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(String tenantId, UUID taskRunId, ChatStreamEvent event) {
        jdbcClient.sql("""
                        INSERT INTO agent_task_events
                            (tenant_id, task_run_id, event_type, payload, sequence_no)
                        VALUES
                            (:tenantId, :taskRunId, :eventType, CAST(:payload AS jsonb), :sequenceNo)
                        """)
                .param("tenantId", tenantId)
                .param("taskRunId", taskRunId)
                .param("eventType", event.type())
                .param("payload", toJson(event.payload()))
                .param("sequenceNo", event.sequence())
                .update();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize task event payload", e);
        }
    }
}
