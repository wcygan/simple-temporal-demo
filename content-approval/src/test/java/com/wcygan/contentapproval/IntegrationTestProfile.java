package com.wcygan.contentapproval;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class IntegrationTestProfile implements QuarkusTestProfile {

    // Static MySQL container for test runtime (not for code generation)
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("contentdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static {
        mysqlContainer.start();
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("quarkus.datasource.db-kind", "mysql");
        config.put("quarkus.datasource.jdbc.url", mysqlContainer.getJdbcUrl());
        config.put("quarkus.datasource.username", mysqlContainer.getUsername());
        config.put("quarkus.datasource.password", mysqlContainer.getPassword());
        config.put("quarkus.jooq.dialect", "MYSQL");
        config.put("quarkus.flyway.migrate-at-start", "true");
        config.put("quarkus.flyway.baseline-on-migrate", "true");
        config.put("quarkus.temporal.start-workers", "false");
        config.put("quarkus.http.test-port", "0");
        config.put("quarkus.log.level", "WARN");
        config.put("quarkus.log.category.\"com.wcygan.contentapproval\".level", "INFO");
        return config;
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}