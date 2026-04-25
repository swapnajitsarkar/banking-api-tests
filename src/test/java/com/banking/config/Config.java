package com.banking.config;

/**
 * Centralised configuration reader.
 * Priority: System property → environment variable → default value.
 * Set BASE_URL env var or -Dbase.url JVM arg to point at a live server.
 */
public class Config {

    private Config() {}

    public static final String BASE_URL =
            resolve("base.url", "BASE_URL", "http://localhost:8080/api");

    public static final int CONNECT_TIMEOUT_MS =
            Integer.parseInt(resolve("connect.timeout", "CONNECT_TIMEOUT", "10000"));

    public static final int READ_TIMEOUT_MS =
            Integer.parseInt(resolve("read.timeout", "READ_TIMEOUT", "15000"));

    public static final String ADMIN_EMAIL =
            resolve("admin.email", "ADMIN_EMAIL", "admin@bank.com");

    public static final String ADMIN_PASSWORD =
            resolve("admin.password", "ADMIN_PASSWORD", "Admin@123");

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String resolve(String sysProp, String envVar, String defaultVal) {
        String sys = System.getProperty(sysProp);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) return env;
        return defaultVal;
    }
}
