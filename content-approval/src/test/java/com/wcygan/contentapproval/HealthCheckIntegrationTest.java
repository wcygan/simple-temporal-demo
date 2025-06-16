package com.wcygan.contentapproval;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class HealthCheckIntegrationTest {

    @Test
    public void testHealthCheckEndpoint() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", notNullValue())
            .body("checks.size()", greaterThan(0));
    }

    @Test
    public void testLivenessCheck() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    public void testReadinessCheck() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    public void testDatabaseHealthCheck() {
        Response response = given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .extract().response();

        String responseBody = response.getBody().asString();
        
        // Verify that database health check exists and is UP
        assertTrue(responseBody.contains("Database connections health check"), 
            "Should include database health check");
        
        // The response should contain the database check as UP
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("checks.find { it.name == 'Database connections health check' }.status", equalTo("UP"));
    }

    @Test
    public void testHealthCheckStructure() {
        // Test the overall structure of health check response
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks", isA(java.util.List.class))
            .body("checks[0]", hasKey("name"))
            .body("checks[0]", hasKey("status"));
    }

    @Test
    public void testHealthCheckResponseTime() {
        // Test that health check responds quickly (under 1 second)
        long startTime = System.currentTimeMillis();
        
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200);
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        
        assertTrue(responseTime < 1000, 
            "Health check should respond in under 1 second, took: " + responseTime + "ms");
    }
}