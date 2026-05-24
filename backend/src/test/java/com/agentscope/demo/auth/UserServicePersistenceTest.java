package com.agentscope.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentscope.demo.tenant.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserServicePersistenceTest {

    @Test
    void registersSameUsernameInDifferentTenantsButRejectsDuplicateWithinTenant() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        UserService service = new UserService(repository);

        TenantContext.runWithTenant("tenant-a", () -> service.register("alice", "secret"));
        TenantContext.runWithTenant("tenant-b", () -> service.register("alice", "secret"));

        assertThat(repository.users).hasSize(2);
        TenantContext.runWithTenant("tenant-a", () ->
                assertThatThrownBy(() -> service.register("alice", "secret"))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    void loginReadsPersistedPasswordHashByTenant() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        UserService firstService = new UserService(repository);
        UserService secondService = new UserService(repository);

        TenantContext.runWithTenant("tenant-a", () -> firstService.register("alice", "secret"));

        TenantContext.runWithTenant("tenant-a", () -> {
            UserAccount user = secondService.login("alice", "secret");
            assertThat(user.tenantId()).isEqualTo("tenant-a");
        });
    }

    private static class InMemoryUserRepository implements UserRepository {
        private final Map<String, UserAccount> users = new HashMap<>();

        @Override
        public Optional<UserAccount> findByTenantIdAndUsername(String tenantId, String username) {
            return Optional.ofNullable(users.get(tenantId + ":" + username));
        }

        @Override
        public UserAccount insert(String tenantId, String username, String passwordHash) {
            UserAccount account = new UserAccount(UUID.randomUUID(), tenantId, username, passwordHash);
            users.put(tenantId + ":" + username, account);
            return account;
        }
    }
}
