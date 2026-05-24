package com.agentscope.demo.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void defaultsToDefaultTenantWhenNoTenantIsBound() {
        TenantContext.clear();

        assertThat(TenantContext.currentTenantId()).isEqualTo("default");
    }

    @Test
    void scopesTenantForCurrentThreadAndClearsAfterUse() {
        TenantContext.runWithTenant("acme", () -> {
            assertThat(TenantContext.currentTenantId()).isEqualTo("acme");
        });

        assertThat(TenantContext.currentTenantId()).isEqualTo("default");
    }
}
