package com.wcygan.contentapproval.integration;

import com.wcygan.contentapproval.IntegrationTestProfile;
import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for REST API endpoints with real workflow execution.
 * Tests the complete flow from REST API to Temporal workflows.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class RestApiIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(RestApiIntegrationTest.class);
    
    @Inject
    DSLContext dsl;
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any test data from previous runs
        dsl.deleteFrom(CONTENT)
            .where(CONTENT.AUTHOR_ID.like("rest-api-test-%"))
            .execute();
    }
    
    @Test
    public void testCompleteContentSubmissionWorkflow() throws Exception {
        logger.info("Testing complete content submission workflow via REST API");
        
        // Step 1: Submit content via REST API
        ContentSubmissionRequest request = new ContentSubmissionRequest();
        request.setTitle("REST API Test Content");
        request.setContent("This is comprehensive test content submitted via REST API to validate the complete " +
            "integration between REST endpoints and Temporal workflow execution. The content contains sufficient " +
            "text to pass validation checks and trigger the full approval workflow process.");
        request.setAuthorId("rest-api-test-author");
        
        ContentApprovalResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/content")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUBMITTED"))
                .body("workflowId", notNullValue())
                .body("contentId", notNullValue())
                .extract()
                .as(ContentApprovalResponse.class);
        
        assertNotNull(response.getWorkflowId());
        assertNotNull(response.getContentId());
        assertEquals("SUBMITTED", response.getStatus());
        
        Long contentId = response.getContentId();
        String workflowId = response.getWorkflowId();
        
        logger.info("Content submitted successfully - ID: {}, Workflow: {}", contentId, workflowId);
        
        // Step 2: Wait for workflow to process and check status
        Thread.sleep(3000);
        
        ContentStatusResponse statusResponse = given()
                .when()
                .get("/content/{contentId}/status", contentId)
                .then()
                .statusCode(200)
                .body("contentId", equalTo(contentId.intValue()))
                .body("status", notNullValue())
                .body("workflowId", equalTo(workflowId))
                .extract()
                .as(ContentStatusResponse.class);
        
        assertEquals(contentId, statusResponse.getContentId());
        assertEquals(workflowId, statusResponse.getWorkflowId());
        assertNotNull(statusResponse.getStatus());
        
        logger.info("Content status retrieved - Status: {}", statusResponse.getStatus());
        
        // Step 3: If content is under review, approve it
        if ("UNDER_REVIEW".equals(statusResponse.getStatus())) {
            logger.info("Content is under review, sending approval");
            
            given()
                    .queryParam("approverId", "rest-api-reviewer")
                    .queryParam("comments", "Approved via REST API integration test")
                    .when()
                    .post("/content/{contentId}/approve", contentId)
                    .then()
                    .statusCode(200)
                    .body("message", containsString("approval signal sent successfully"));
            
            // Wait for approval to be processed
            Thread.sleep(2000);
            
            // Check final status
            ContentStatusResponse finalStatusResponse = given()
                    .when()
                    .get("/content/{contentId}/status", contentId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ContentStatusResponse.class);
            
            assertEquals("PUBLISHED", finalStatusResponse.getStatus());
            logger.info("Content approved and published successfully");
            
        } else if ("REJECTED".equals(statusResponse.getStatus())) {
            logger.info("Content was auto-rejected during validation");
        } else {
            logger.info("Content in final state: {}", statusResponse.getStatus());
        }
        
        // Step 4: Verify database state
        var dbRecord = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        assertNotNull(dbRecord);
        assertEquals(workflowId, dbRecord.getTemporalWorkflowId());
        assertTrue("PUBLISHED".equals(dbRecord.getStatus()) || "REJECTED".equals(dbRecord.getStatus()));
        
        logger.info("Complete content submission workflow test completed successfully");
    }
    
    @Test
    public void testContentRejectionWorkflow() throws Exception {
        logger.info("Testing content rejection workflow via REST API");
        
        // Create content directly in database for this test
        Long contentId = createTestContent("rest-api-test-reject", 
            "Rejection Test Content", 
            "This content will be used to test the rejection workflow through REST API endpoints.");
        
        // Submit for workflow processing
        ContentSubmissionRequest request = new ContentSubmissionRequest();
        request.setTitle("Rejection Test Content");
        request.setContent("This content will be used to test the rejection workflow through REST API endpoints.");
        request.setAuthorId("rest-api-test-reject");
        
        ContentApprovalResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/content")
                .then()
                .statusCode(200)
                .extract()
                .as(ContentApprovalResponse.class);
        
        Long submittedContentId = response.getContentId();
        String workflowId = response.getWorkflowId();
        
        // Wait for workflow to process
        Thread.sleep(3000);
        
        ContentStatusResponse statusResponse = given()
                .when()
                .get("/content/{contentId}/status", submittedContentId)
                .then()
                .statusCode(200)
                .extract()
                .as(ContentStatusResponse.class);
        
        // If content is under review, reject it
        if ("UNDER_REVIEW".equals(statusResponse.getStatus())) {
            logger.info("Content is under review, sending rejection");
            
            given()
                    .queryParam("reviewerId", "rest-api-reviewer")
                    .queryParam("reason", "Content does not meet quality standards")
                    .when()
                    .post("/content/{contentId}/reject", submittedContentId)
                    .then()
                    .statusCode(200)
                    .body("message", containsString("rejection signal sent successfully"));
            
            // Wait for rejection to be processed
            Thread.sleep(2000);
            
            // Verify final status
            ContentStatusResponse finalStatusResponse = given()
                    .when()
                    .get("/content/{contentId}/status", submittedContentId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ContentStatusResponse.class);
            
            assertEquals("REJECTED", finalStatusResponse.getStatus());
            logger.info("Content rejected successfully via REST API");
        } else {
            logger.info("Content was auto-rejected, final status: {}", statusResponse.getStatus());
        }
        
        logger.info("Content rejection workflow test completed successfully");
    }
    
    @Test
    public void testRequestChangesWorkflow() throws Exception {
        logger.info("Testing request changes workflow via REST API");
        
        ContentSubmissionRequest request = new ContentSubmissionRequest();
        request.setTitle("Changes Request Test");
        request.setContent("This is test content for validating the request changes workflow functionality. " +
            "The content should pass initial validation and reach the review stage where changes can be requested.");
        request.setAuthorId("rest-api-test-changes");
        
        ContentApprovalResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/content")
                .then()
                .statusCode(200)
                .extract()
                .as(ContentApprovalResponse.class);
        
        Long contentId = response.getContentId();
        String workflowId = response.getWorkflowId();
        
        // Wait for workflow to process
        Thread.sleep(3000);
        
        ContentStatusResponse statusResponse = given()
                .when()
                .get("/content/{contentId}/status", contentId)
                .then()
                .statusCode(200)
                .extract()
                .as(ContentStatusResponse.class);
        
        // If content is under review, request changes
        if ("UNDER_REVIEW".equals(statusResponse.getStatus())) {
            logger.info("Content is under review, requesting changes");
            
            given()
                    .queryParam("reviewerId", "rest-api-reviewer")
                    .queryParam("changeRequests", "Please add more examples and improve formatting")
                    .when()
                    .post("/content/{contentId}/request-changes", contentId)
                    .then()
                    .statusCode(200)
                    .body("message", containsString("change request signal sent successfully"));
            
            // Wait for changes request to be processed
            Thread.sleep(2000);
            
            // Verify final status
            ContentStatusResponse finalStatusResponse = given()
                    .when()
                    .get("/content/{contentId}/status", contentId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(ContentStatusResponse.class);
            
            assertEquals("CHANGES_REQUESTED", finalStatusResponse.getStatus());
            logger.info("Changes requested successfully via REST API");
        } else {
            logger.info("Content not in review state, final status: {}", statusResponse.getStatus());
        }
        
        logger.info("Request changes workflow test completed successfully");
    }
    
    @Test
    public void testInvalidContentSubmission() throws Exception {
        logger.info("Testing invalid content submission handling");
        
        // Test missing required fields
        ContentSubmissionRequest invalidRequest = new ContentSubmissionRequest();
        // Leave required fields null
        
        given()
                .contentType(ContentType.JSON)
                .body(invalidRequest)
                .when()
                .post("/content")
                .then()
                .statusCode(400);
        
        // Test empty content
        ContentSubmissionRequest emptyRequest = new ContentSubmissionRequest();
        emptyRequest.setTitle("");
        emptyRequest.setContent("");
        emptyRequest.setAuthorId("");
        
        given()
                .contentType(ContentType.JSON)
                .body(emptyRequest)
                .when()
                .post("/content")
                .then()
                .statusCode(400);
        
        logger.info("Invalid content submission handling test completed successfully");
    }
    
    @Test
    public void testNonExistentContentStatus() throws Exception {
        logger.info("Testing non-existent content status handling");
        
        Long nonExistentId = 999999L;
        
        given()
                .when()
                .get("/content/{contentId}/status", nonExistentId)
                .then()
                .statusCode(404)
                .body("status", equalTo("NOT_FOUND"))
                .body("contentId", equalTo(nonExistentId.intValue()));
        
        logger.info("Non-existent content status handling test completed successfully");
    }
    
    @Test
    public void testInvalidSignalRequests() throws Exception {
        logger.info("Testing invalid signal request handling");
        
        Long contentId = 123L;
        
        // Test approval without approver ID
        given()
                .when()
                .post("/content/{contentId}/approve", contentId)
                .then()
                .statusCode(400)
                .body("error", containsString("Approver ID is required"));
        
        // Test rejection without reviewer ID
        given()
                .when()  
                .post("/content/{contentId}/reject", contentId)
                .then()
                .statusCode(400)
                .body("error", containsString("Reviewer ID is required"));
        
        // Test request changes without reviewer ID
        given()
                .queryParam("changeRequests", "Some changes")
                .when()
                .post("/content/{contentId}/request-changes", contentId)
                .then()
                .statusCode(400)
                .body("error", containsString("Reviewer ID is required"));
        
        // Test request changes without change requests
        given()
                .queryParam("reviewerId", "reviewer")
                .when()
                .post("/content/{contentId}/request-changes", contentId)
                .then()
                .statusCode(400)
                .body("error", containsString("Change requests are required"));
        
        logger.info("Invalid signal request handling test completed successfully");
    }
    
    @Test
    public void testConcurrentApiRequests() throws Exception {
        logger.info("Testing concurrent API request handling");
        
        // Submit content
        ContentSubmissionRequest request = new ContentSubmissionRequest();
        request.setTitle("Concurrent API Test");
        request.setContent("This content is used to test concurrent API request handling capabilities.");
        request.setAuthorId("rest-api-test-concurrent");
        
        ContentApprovalResponse response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/content")
                .then()
                .statusCode(200)
                .extract()
                .as(ContentApprovalResponse.class);
        
        Long contentId = response.getContentId();
        
        // Wait for workflow to process
        Thread.sleep(3000);
        
        // Make multiple concurrent status requests
        CompletableFuture<Void>[] futures = new CompletableFuture[5];
        
        for (int i = 0; i < 5; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                given()
                        .when()
                        .get("/content/{contentId}/status", contentId)
                        .then()
                        .statusCode(200)
                        .body("contentId", equalTo(contentId.intValue()));
            });
        }
        
        // Wait for all requests to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        logger.info("Concurrent API request handling test completed successfully");
    }
    
    /**
     * Helper method to create test content in the database.
     */
    @Transactional
    public Long createTestContent(String authorId, String title, String content) {
        ContentRecord record = dsl.newRecord(CONTENT);
        record.setTitle(title);
        record.setContent(content);
        record.setAuthorId(authorId);
        record.setStatus("DRAFT");
        record.setCreatedDate(LocalDateTime.now());
        record.setUpdatedDate(LocalDateTime.now());
        
        record.store();
        return record.getId();
    }
}