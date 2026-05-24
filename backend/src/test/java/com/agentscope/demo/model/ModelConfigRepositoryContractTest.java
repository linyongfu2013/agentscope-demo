package com.agentscope.demo.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelConfigRepositoryContractTest {

    @Test
    void namedRepositoryMethodsAreAvailableForTenantCrud() throws Exception {
        assertThat(ModelConfigRepository.class.getMethod("findByTenantIdAndId", String.class, UUID.class)).isNotNull();
        assertThat(ModelConfigRepository.class.getMethod("deleteByTenantIdAndId", String.class, UUID.class)).isNotNull();
        assertThat(ModelConfigRepository.class.getMethod("findByTenantIdAndEnabledTrueOrderByCreatedAtDesc", String.class)).isNotNull();
    }
}
