package com.agentscope.demo.auth;

import java.util.UUID;

public record UserAccount(UUID id, String tenantId, String username, String passwordHash) {
}
