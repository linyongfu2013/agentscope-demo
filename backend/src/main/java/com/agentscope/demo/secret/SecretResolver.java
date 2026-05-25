package com.agentscope.demo.secret;

import java.util.Optional;

public interface SecretResolver {

    Optional<String> resolve(String ref);
}
