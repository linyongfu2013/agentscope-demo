package com.agentscope.demo.auth;

import com.agentscope.demo.tenant.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcClient jdbcClient;
    private final TenantRepository tenantRepository;

    public JdbcUserRepository(JdbcClient jdbcClient, TenantRepository tenantRepository) {
        this.jdbcClient = jdbcClient;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<UserAccount> findByTenantIdAndUsername(String tenantId, String username) {
        return jdbcClient.sql("""
                        SELECT id, tenant_id, username, password_hash
                        FROM users
                        WHERE tenant_id = :tenantId AND username = :username
                        """)
                .param("tenantId", tenantId)
                .param("username", username)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getString("username"),
                        rs.getString("password_hash")
                ))
                .optional();
    }

    @Override
    public UserAccount insert(String tenantId, String username, String passwordHash) {
        tenantRepository.ensureExists(tenantId);
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO users (id, tenant_id, username, password_hash)
                        VALUES (:id, :tenantId, :username, :passwordHash)
                        """)
                .param("id", id)
                .param("tenantId", tenantId)
                .param("username", username)
                .param("passwordHash", passwordHash)
                .update();
        return new UserAccount(id, tenantId, username, passwordHash);
    }
}
