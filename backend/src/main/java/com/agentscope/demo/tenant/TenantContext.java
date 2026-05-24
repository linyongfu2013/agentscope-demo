package com.agentscope.demo.tenant;

public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "default";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String currentTenantId() {
        String tenantId = CURRENT.get();
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId;
    }

    public static void setTenantId(String tenantId) {
        CURRENT.set(tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runWithTenant(String tenantId, Runnable action) {
        String previous = CURRENT.get();
        setTenantId(tenantId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
