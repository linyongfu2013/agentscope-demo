package com.agentscope.demo.secret;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretResolver implements SecretResolver {

    private final Environment environment;

    public EnvironmentSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        String key = ref.trim();
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(key);
        }
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
