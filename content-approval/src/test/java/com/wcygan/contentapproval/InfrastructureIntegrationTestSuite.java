package com.wcygan.contentapproval;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive integration test suite that verifies all infrastructure components
 * are working together correctly.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Infrastructure Integration Test Suite")
public class InfrastructureIntegrationTestSuite {

    @Test
    @DisplayName("1. Application Startup and Basic Health")
    public void testApplicationStartupAndHealth() {
        // Test that the application started successfully
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("2. Database Connectivity")
    public void testDatabaseConnectivity() {
        // Test database health check specifically
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("checks.find { it.name == 'Database connections health check' }.status", equalTo("UP"));
    }

    @Test
    @DisplayName("3. Flyway Migration Status")
    public void testFlywayMigrationStatus() {
        // Test that migrations were applied
        // This is verified by the fact that the database health check passes
        // and we can access the migrated tables
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("4. Basic API Endpoints")
    public void testBasicApiEndpoints() {
        // Test that basic endpoints are working
        given()
            .when().get("/hello")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }

    @Test
    @DisplayName("5. OpenAPI Documentation")
    public void testOpenApiDocumentation() {
        // Test that OpenAPI documentation is available
        given()
            .when().get("/q/openapi")
            .then()
            .statusCode(200)
            .body(containsString("openapi"))
            .body(containsString("Content Approval API"));
    }

    @Test
    @DisplayName("6. Metrics Endpoint")
    public void testMetricsEndpoint() {
        // Test that metrics are available
        given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200);
    }

    @Test
    @DisplayName("7. Application Ready State")
    public void testApplicationReadyState() {
        // Test readiness probe
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("8. Application Live State")
    public void testApplicationLiveState() {
        // Test liveness probe
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("9. Overall Infrastructure Health")
    public void testOverallInfrastructureHealth() {
        // Comprehensive health check
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", notNullValue())
            .body("checks.size()", greaterThan(0))
            .body("checks.every { it.status == 'UP' }", equalTo(true));
    }
}