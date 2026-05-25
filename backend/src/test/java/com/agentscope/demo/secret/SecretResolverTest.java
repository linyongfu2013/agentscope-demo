package com.agentscope.demo.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretResolverTest {

    @Test
    void resolvesEnvironmentStyleReferencesFromConfiguredMap() {
        SecretResolver resolver = new MapSecretResolver(Map.of("OPENAI_API_KEY", "sk-test"));

        assertThat(resolver.resolve("OPENAI_API_KEY")).contains("sk-test");
    }

    @Test
    void treatsBlankReferencesAsMissing() {
        SecretResolver resolver = new MapSecretResolver(Map.of("OPENAI_API_KEY", "sk-test"));

        assertThat(resolver.resolve(" ")).isEmpty();
    }
}
