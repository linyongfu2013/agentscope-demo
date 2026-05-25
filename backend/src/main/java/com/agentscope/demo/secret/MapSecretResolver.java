package com.agentscope.demo.secret;

import java.util.Map;
import java.util.Optional;

public class MapSecretResolver implements SecretResolver {

    private final Map<String, String> secrets;

    public MapSecretResolver(Map<String, String> secrets) {
        this.secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    @Override
    public Optional<String> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String value = secrets.get(ref.trim());
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
