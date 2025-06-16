package com.wcygan.contentapproval.integration;

import com.wcygan.contentapproval.IntegrationTestProfile;
import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the complete content approval workflow.
 * Tests the full stack from REST API through Temporal workflows to database persistence.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class ContentApprovalE2ETest {
    
    @Inject
    DSLContext dsl;
    
    @BeforeEach
    @Transactional
    public void cleanupTestData() {
        // Clean up any test data from previous runs
        dsl.deleteFrom(CONTENT)
            .where(CONTENT.AUTHOR_ID.like("test-e2e-%"))
            .execute();
    }
    
    @Test
    public void testCompleteApprovalWorkflow() throws InterruptedException {
        // Step 1: Submit content for approval
        ContentSubmissionRequest request = new ContentSubmissionRequest(
            "E2E Test Article: Advanced Temporal Patterns",
            "This is a comprehensive test article that demonstrates various Temporal workflow patterns. " +
            "It covers topics such as workflow versioning, activity retry policies, signal handling, " +
            "and query methods. This article is designed to pass all validation checks and provide " +
            "valuable content for our readers. The content is well-structured with clear examples " +
            "and practical implementations that developers can use in their own projects.",
            "test-e2e-author-1"
        );
        request.setTags(Arrays.asList("temporal", "workflow", "testing", "e2e"));
        
        // Submit via REST API
        ContentApprovalResponse submissionResponse = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/content")
            .then()
            .statusCode(200)
            .body("contentId", notNullValue())
            .body("workflowId", notNullValue())
            .body("status", equalTo("SUBMITTED"))
            .extract()
            .as(ContentApprovalResponse.class);
        
        Long contentId = submissionResponse.getContentId();
        String workflowId = submissionResponse.getWorkflowId();
        
        assertNotNull(contentId);
        assertNotNull(workflowId);
        
        // Step 2: Wait for workflow to process and reach UNDER_REVIEW status
        Thread.sleep(2000); // Give workflow time to start and process
        
        // Check status via REST API
        ContentStatusResponse statusResponse = given()
            .when()
            .get("/content/{contentId}/status", contentId)
            .then()
            .statusCode(200)
            .body("contentId", equalTo(contentId.intValue()))
            .body("workflowId", equalTo(workflowId))
            .body("status", equalTo("UNDER_REVIEW"))
            .body("complete", equalTo(false))
            .extract()
            .as(ContentStatusResponse.class);
        
        assertEquals("UNDER_REVIEW", statusResponse.getStatus());
        assertFalse(statusResponse.isComplete());
        
        // Step 3: Approve the content
        given()
            .queryParam("approverId", "test-reviewer-1")
            .queryParam("comments", "Excellent article! Well written and informative.")
            .when()
            .post("/content/{contentId}/approve", contentId)
            .then()
            .statusCode(200)
            .body("message", containsString("approval signal sent successfully"));
        
        // Step 4: Wait for approval to be processed
        Thread.sleep(2000);
        
        // Step 5: Verify final status is PUBLISHED
        given()
            .when()
            .get("/content/{contentId}/status", contentId)
            .then()
            .statusCode(200)
            .body("contentId", equalTo(contentId.intValue()))
            .body("status", equalTo("PUBLISHED"))
            .body("complete", equalTo(true))
            .body("currentReviewerId", equalTo("test-reviewer-1"))
            .body("approvalComments", equalTo("Excellent article! Well written and informative."));
        
        // Step 6: Verify database state
        var contentRecord = dsl.selectFrom(CONTENT)
            .where(CONTENT.ID.eq(contentId))
            .fetchOne();
        
        assertNotNull(contentRecord);
        assertEquals("PUBLISHED", contentRecord.getStatus());
        assertEquals(workflowId, contentRecord.getTemporalWorkflowId());
        assertEquals("test-e2e-author-1", contentRecord.getAuthorId());
    }
    
    @Test
    public void testRejectionWorkflow() throws InterruptedException {
        // Step 1: Submit content for approval
        ContentSubmissionRequest request = new ContentSubmissionRequest(
            "E2E Test: Content to be Rejected",
            "This content will be rejected during the test to verify the rejection workflow. " +
            "It contains adequate content to pass validation but will be manually rejected " +
            "by the reviewer to test the rejection flow. This allows us to verify that " +
            "rejection signals are properly handled and the workflow state is updated correctly.",
            "test-e2e-author-2"
        );
        
        ContentApprovalResponse submissionResponse = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/content")
            .then()
            .statusCode(200)
            .extract()
            .as(ContentApprovalResponse.class);
        
        Long contentId = submissionResponse.getContentId();
        
        // Step 2: Wait for workflow to reach UNDER_REVIEW
        Thread.sleep(2000);
        
        // Step 3: Reject the content
        given()
            .queryParam("reviewerId", "test-reviewer-2")
            .queryParam("reason", "Content does not meet our quality standards")
            .when()
            .post("/content/{contentId}/reject", contentId)
            .then()
            .statusCode(200)
            .body("message", containsString("rejection signal sent successfully"));
        
        // Step 4: Wait for rejection to be processed
        Thread.sleep(2000);
        
        // Step 5: Verify final status is REJECTED
        given()
            .when()
            .get("/content/{contentId}/status", contentId)
            .then()
            .statusCode(200)
            .body("contentId", equalTo(contentId.intValue()))
            .body("status", equalTo("REJECTED"))
            .body("complete", equalTo(true))
            .body("currentReviewerId", equalTo("test-reviewer-2"))
            .body("rejectionReason", equalTo("Content does not meet our quality standards"));
        
        // Step 6: Verify database state
        var contentRecord = dsl.selectFrom(CONTENT)
            .where(CONTENT.ID.eq(contentId))
            .fetchOne();
        
        assertNotNull(contentRecord);
        assertEquals("REJECTED", contentRecord.getStatus());
    }
    
    @Test
    public void testChangeRequestWorkflow() throws InterruptedException {
        // Step 1: Submit content for approval
        ContentSubmissionRequest request = new ContentSubmissionRequest(
            "E2E Test: Content Requiring Changes",
            "This content will have changes requested to test the change request workflow. " +
            "The content is valid but will be marked as needing improvements to verify " +
            "that change request signals work correctly and update the workflow state appropriately.",
            "test-e2e-author-3"
        );
        
        ContentApprovalResponse submissionResponse = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/content")
            .then()
            .statusCode(200)
            .extract()
            .as(ContentApprovalResponse.class);
        
        Long contentId = submissionResponse.getContentId();
        
        // Step 2: Wait for workflow to reach UNDER_REVIEW
        Thread.sleep(2000);
        
        // Step 3: Request changes
        given()
            .queryParam("reviewerId", "test-reviewer-3")
            .queryParam("changeRequests", "Please add more examples and improve the conclusion section")
            .when()
            .post("/content/{contentId}/request-changes", contentId)
            .then()
            .statusCode(200)
            .body("message", containsString("change request signal sent successfully"));
        
        // Step 4: Wait for change request to be processed
        Thread.sleep(2000);
        
        // Step 5: Verify status is CHANGES_REQUESTED
        given()
            .when()
            .get("/content/{contentId}/status", contentId)
            .then()
            .statusCode(200)
            .body("contentId", equalTo(contentId.intValue()))
            .body("status", equalTo("CHANGES_REQUESTED"))
            .body("complete", equalTo(false))
            .body("currentReviewerId", equalTo("test-reviewer-3"))
            .body("changeRequests", equalTo("Please add more examples and improve the conclusion section"))
            .body("revisionCount", equalTo(1));
        
        // Step 6: Verify database state
        var contentRecord = dsl.selectFrom(CONTENT)
            .where(CONTENT.ID.eq(contentId))
            .fetchOne();
        
        assertNotNull(contentRecord);
        assertEquals("CHANGES_REQUESTED", contentRecord.getStatus());
    }
    
    @Test
    public void testValidationFailure() throws InterruptedException {
        // Submit content that will fail validation (too short)
        ContentSubmissionRequest request = new ContentSubmissionRequest(
            "Short",
            "Too short content", // This is too short and will fail validation
            "test-e2e-author-4"
        );
        
        ContentApprovalResponse submissionResponse = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/content")
            .then()
            .statusCode(200)
            .extract()
            .as(ContentApprovalResponse.class);
        
        Long contentId = submissionResponse.getContentId();
        
        // Wait for workflow to process validation
        Thread.sleep(3000); // Longer wait as validation failure might take more time
        
        // Verify content was auto-rejected due to validation failure
        given()
            .when()
            .get("/content/{contentId}/status", contentId)
            .then()
            .statusCode(200)
            .body("contentId", equalTo(contentId.intValue()))
            .body("status", equalTo("REJECTED"))
            .body("complete", equalTo(true))
            .body("currentReviewerId", equalTo("system"))
            .body("rejectionReason", equalTo("Content failed automated validation"));
        
        // Verify database state
        var contentRecord = dsl.selectFrom(CONTENT)
            .where(CONTENT.ID.eq(contentId))
            .fetchOne();
        
        assertNotNull(contentRecord);
        assertEquals("REJECTED", contentRecord.getStatus());
    }
    
    @Test
    public void testInvalidRequests() {
        // Test missing approver ID
        given()
            .when()
            .post("/content/999/approve")
            .then()
            .statusCode(400)
            .body("error", equalTo("Approver ID is required"));
        
        // Test missing reviewer ID for rejection
        given()
            .when()
            .post("/content/999/reject")
            .then()
            .statusCode(400)
            .body("error", equalTo("Reviewer ID is required"));
        
        // Test missing change requests
        given()
            .queryParam("reviewerId", "test-reviewer")
            .when()
            .post("/content/999/request-changes")
            .then()
            .statusCode(400)
            .body("error", equalTo("Change requests are required"));
        
        // Test non-existent content status
        given()
            .when()
            .get("/content/999999/status")
            .then()
            .statusCode(404)
            .body("status", equalTo("NOT_FOUND"));
    }
}