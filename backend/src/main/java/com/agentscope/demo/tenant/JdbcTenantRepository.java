package com.agentscope.demo.tenant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    private final JdbcClient jdbcClient;

    public JdbcTenantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void ensureExists(String tenantId) {
        jdbcClient.sql("""
                        INSERT INTO tenants (id, name)
                        VALUES (:id, :name)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", tenantId)
                .param("name", tenantId)
                .update();
    }
}
