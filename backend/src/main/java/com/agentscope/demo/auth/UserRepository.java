package com.agentscope.demo.auth;

import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findByTenantIdAndUsername(String tenantId, String username);

    UserAccount insert(String tenantId, String username, String passwordHash);
}
