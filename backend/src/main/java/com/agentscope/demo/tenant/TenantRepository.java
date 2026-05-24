package com.agentscope.demo.tenant;

public interface TenantRepository {

    void ensureExists(String tenantId);
}
