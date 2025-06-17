package com.wcygan.contentapproval;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Universal test profile using MySQL TestContainers.
 * Provides production-parity testing environment for all tests.
 */
public class IntegrationTestProfile implements QuarkusTestProfile {

    // Shared MySQL container for all tests - optimized for performance
    private static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("content_approval_test")
            .withUsername("testuser")
            .withPassword("testpass")
            // Performance optimizations for testing
            .withCommand(
                "--innodb-buffer-pool-size=64M",
                "--innodb-log-file-size=32M", 
                "--innodb-flush-log-at-trx-commit=0",
                "--sync-binlog=0",
                "--innodb-doublewrite=0",
                "--innodb-flush-method=O_DIRECT_NO_FSYNC",
                "--skip-log-bin"
            )
            .withReuse(true); // Enable container reuse for faster test execution

    static {
        mysqlContainer.start();
        // Ensure graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(mysqlContainer::stop));
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new java.util.HashMap<>();
        
        // Database configuration using the shared container
        config.put("quarkus.datasource.db-kind", "mysql");
        config.put("quarkus.datasource.jdbc.url", mysqlContainer.getJdbcUrl());
        config.put("quarkus.datasource.username", mysqlContainer.getUsername());
        config.put("quarkus.datasource.password", mysqlContainer.getPassword());
        
        // jOOQ configuration
        config.put("quarkus.jooq.dialect", "MYSQL");
        
        // Flyway configuration 
        config.put("quarkus.flyway.migrate-at-start", "true");
        config.put("quarkus.flyway.baseline-on-migrate", "true");
        config.put("quarkus.flyway.clean-at-start", "false"); // Don't clean between tests for speed
        
        // Temporal configuration optimized for testing
        config.put("quarkus.temporal.namespace", "content-approval-test");
        config.put("quarkus.temporal.start-workers", "false");
        
        // Test environment optimizations
        config.put("quarkus.http.test-port", "0");
        config.put("quarkus.log.level", "WARN");
        config.put("quarkus.log.category.\"com.wcygan.contentapproval\".level", "INFO");
        config.put("quarkus.test.continuous-testing", "disabled");
        
        // Disable dev services since we're using explicit TestContainers
        config.put("quarkus.datasource.devservices.enabled", "false");
        
        return config;
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
    
    /**
     * Get the shared MySQL container for tests that need direct database access.
     * @return The shared MySQL container instance
     */
    public static MySQLContainer<?> getMySQLContainer() {
        return mysqlContainer;
    }
}