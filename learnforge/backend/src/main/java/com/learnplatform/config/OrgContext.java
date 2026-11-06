package com.learnplatform.config;

import java.util.UUID;

/** The organisation of the request being handled, so shared services can meter usage. */
public final class OrgContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private OrgContext() {}

    public static void set(UUID orgId) { CURRENT.set(orgId); }
    public static UUID get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
