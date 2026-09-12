package com.lab.todo.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fails the process, loudly and immediately, if the container was started
 * without a variable the application cannot work without.
 *
 * <p>All configuration comes from the environment: the non-secret half from
 * the task definition's {@code environment} block, the credentials from its
 * {@code secrets} block, which ECS resolves from Secrets Manager before the
 * container starts. Nothing is read from a properties file at runtime and
 * nothing has a baked-in default that would let a misconfigured task come up
 * pointing at the wrong database.
 */
public final class EnvironmentValidator {

    private static final List<String> REQUIRED = List.of(
            "SERVER_PORT",
            "DB_HOST",
            "DB_PORT",
            "DB_NAME",
            "DB_USERNAME",
            "DB_PASSWORD",
            "REDIS_HOST",
            "REDIS_PORT",
            "REDIS_AUTH_TOKEN");

    private EnvironmentValidator() {
    }

    public static void validateOrExit(Map<String, String> environment) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED) {
            String value = environment.get(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        // Every missing variable at once. Reporting them one restart at a time
        // turns a single typo into a ten minute debugging session.
        System.err.println("FATAL: required environment variables are missing or empty: "
                + String.join(", ", missing));
        System.err.println("DB_* come from the RDS Proxy endpoint and the RDS-managed secret; "
                + "REDIS_* from the ElastiCache endpoint and the generated AUTH token. "
                + "See ecs/taskdef.json.");
        System.exit(1);
    }
}
