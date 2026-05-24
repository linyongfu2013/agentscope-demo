package com.agentscope.demo.auth;

import java.util.UUID;

public record AuthResponse(UUID userId, String token) {
}
