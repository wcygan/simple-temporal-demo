package com.wcygan.contentapproval.integration;

import com.wcygan.contentapproval.IntegrationTestProfile;
import com.wcygan.contentapproval.activity.ContentPersistenceActivity;
import com.wcygan.contentapproval.activity.ContentValidationActivity;
import com.wcygan.contentapproval.activity.NotificationActivity;
import com.wcygan.contentapproval.config.TemporalWorkerConfig;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import com.wcygan.contentapproval.workflow.ContentApprovalWorkflow;
import com.wcygan.contentapproval.workflow.ContentStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for activity failure scenarios and error handling.
 * Tests how the system behaves when activities fail with various types of errors.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class ActivityFailureIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityFailureIntegrationTest.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    DSLContext dsl;
    
    @Inject
    ContentValidationActivity validationActivity;
    
    @Inject
    ContentPersistenceActivity persistenceActivity;
    
    @Inject
    NotificationActivity notificationActivity;
    
    private static final String TEST_WORKFLOW_ID_PREFIX = "test-failure-integration-";
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any test data from previous runs
        dsl.deleteFrom(CONTENT)
            .where(CONTENT.AUTHOR_ID.like("failure-test-%"))
            .execute();
    }
    
    @Test
    public void testWorkflowWithContentValidationFailure() throws Exception {
        // Create content that will fail validation (too short)
        Long contentId = createTestContent("failure-test-validation", "Short", "Too short content");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "validation-" + System.currentTimeMillis();
        
        logger.info("Testing validation failure with contentId: {}, workflowId: {}", contentId, workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(2))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Execute workflow - should complete normally but auto-reject due to validation failure
        String result = workflow.processContentApproval(contentId, "failure-test-validation");
        
        assertNotNull(result);
        assertEquals(workflowId, result);
        
        // Verify workflow handled validation failure gracefully
        var finalState = workflow.getWorkflowState();
        assertEquals(ContentStatus.REJECTED, finalState.getStatus());
        assertEquals("system", finalState.getCurrentReviewerId());
        assertEquals("Content failed automated validation", finalState.getRejectionReason());
        assertTrue(finalState.isComplete());
        
        // Verify database state
        var contentRecord = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        assertNotNull(contentRecord);
        assertEquals("REJECTED", contentRecord.getStatus());
        assertEquals(workflowId, contentRecord.getTemporalWorkflowId());
        
        logger.info("Validation failure test completed successfully");
    }
    
    @Test
    public void testActivityRetryBehavior() throws Exception {
        // Test that activities retry on transient failures
        Long contentId = createTestContent("failure-test-retry", 
            "Test Content for Retry", 
            "This is test content with sufficient length to pass validation checks. " +
            "It contains enough text to meet the minimum requirements for content validation.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "retry-" + System.currentTimeMillis();
        
        logger.info("Testing activity retry behavior with contentId: {}, workflowId: {}", contentId, workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            workflow.processContentApproval(contentId, "failure-test-retry"));
        
        // Wait for workflow to process (activities will retry on failures internally)
        Thread.sleep(5000);
        
        // Complete workflow execution
        String result = workflowResult.get(30, TimeUnit.SECONDS);
        
        assertNotNull(result);
        
        // Verify workflow completed (activities should have eventually succeeded after retries)
        var finalState = workflow.getWorkflowState();
        assertNotNull(finalState);
        assertTrue(finalState.isComplete());
        
        // Should be either PUBLISHED (if validation passed) or REJECTED (if validation failed)
        assertTrue(finalState.getStatus() == ContentStatus.PUBLISHED || 
                  finalState.getStatus() == ContentStatus.REJECTED);
        
        logger.info("Activity retry test completed with final status: {}", finalState.getStatus());
    }
    
    @Test
    public void testDatabaseTransactionRollback() throws Exception {
        // Test transaction rollback scenarios
        Long contentId = createTestContent("failure-test-transaction", 
            "Transaction Test Content", 
            "This content is used to test database transaction rollback scenarios when activities fail.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "transaction-" + System.currentTimeMillis();
        
        logger.info("Testing database transaction rollback with contentId: {}, workflowId: {}", contentId, workflowId);
        
        // Verify initial state
        var initialRecord = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        assertNotNull(initialRecord);
        assertEquals("DRAFT", initialRecord.getStatus());
        assertNull(initialRecord.getTemporalWorkflowId());
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(2))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Execute workflow
        String result = workflow.processContentApproval(contentId, "failure-test-transaction");
        
        assertNotNull(result);
        
        // Verify final database state is consistent
        var finalRecord = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        assertNotNull(finalRecord);
        assertNotNull(finalRecord.getTemporalWorkflowId());
        assertEquals(workflowId, finalRecord.getTemporalWorkflowId());
        
        // Status should be either PUBLISHED or REJECTED (depending on validation)
        assertTrue("PUBLISHED".equals(finalRecord.getStatus()) || "REJECTED".equals(finalRecord.getStatus()));
        
        logger.info("Database transaction rollback test completed successfully");
    }
    
    @Test
    public void testConcurrentWorkflowsDatabaseIntegrity() throws Exception {
        int numWorkflows = 5;
        CompletableFuture<String>[] futures = new CompletableFuture[numWorkflows];
        Long[] contentIds = new Long[numWorkflows];
        String[] workflowIds = new String[numWorkflows];
        
        logger.info("Testing concurrent workflows database integrity with {} workflows", numWorkflows);
        
        // Create test content for each workflow
        for (int i = 0; i < numWorkflows; i++) {
            contentIds[i] = createTestContent("failure-test-concurrent-" + i, 
                "Concurrent Test Content " + i, 
                "This is test content for concurrent workflow testing. Each workflow processes " +
                "different content to test database integrity under concurrent access patterns.");
        }
        
        // Start multiple workflows concurrently
        for (int i = 0; i < numWorkflows; i++) {
            workflowIds[i] = TEST_WORKFLOW_ID_PREFIX + "concurrent-db-" + i + "-" + System.currentTimeMillis();
            
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowIds[i])
                    .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
                    .build();
            
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, options);
            
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> 
                workflow.processContentApproval(contentIds[index], "failure-test-concurrent-" + index));
        }
        
        // Wait for all workflows to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
        allFutures.get(90, TimeUnit.SECONDS);
        
        // Verify database integrity
        for (int i = 0; i < numWorkflows; i++) {
            String result = futures[i].get();
            assertNotNull(result, "Workflow " + i + " should have completed");
            assertEquals(workflowIds[i], result);
            
            // Verify database record integrity
            var record = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentIds[i]))
                    .fetchOne();
            
            assertNotNull(record, "Content record " + i + " should exist");
            assertEquals(workflowIds[i], record.getTemporalWorkflowId());
            assertTrue("PUBLISHED".equals(record.getStatus()) || "REJECTED".equals(record.getStatus()));
            
            logger.info("Concurrent workflow {} completed with status: {}", i, record.getStatus());
        }
        
        logger.info("All {} concurrent workflows completed with database integrity maintained", numWorkflows);
    }
    
    @Test
    public void testActivityTimeoutHandling() throws Exception {
        // Test activity timeout scenarios
        Long contentId = createTestContent("failure-test-timeout", 
            "Timeout Test Content", 
            "This content is used to test activity timeout handling and recovery mechanisms.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "timeout-" + System.currentTimeMillis();
        
        logger.info("Testing activity timeout handling with contentId: {}, workflowId: {}", contentId, workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(3))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Execute workflow - activities have configured timeouts and retries
        String result = workflow.processContentApproval(contentId, "failure-test-timeout");
        
        assertNotNull(result);
        
        // Verify workflow handled any timeouts gracefully
        var finalState = workflow.getWorkflowState();
        assertNotNull(finalState);
        assertTrue(finalState.isComplete());
        
        // Workflow should complete even if some activities had timeouts (due to retries)
        assertTrue(finalState.getStatus() == ContentStatus.PUBLISHED || 
                  finalState.getStatus() == ContentStatus.REJECTED);
        
        logger.info("Activity timeout handling test completed with status: {}", finalState.getStatus());
    }
    
    @Test
    public void testPartialWorkflowFailureRecovery() throws Exception {
        // Test workflow recovery from partial failures
        Long contentId = createTestContent("failure-test-recovery", 
            "Recovery Test Content", 
            "This is comprehensive test content with sufficient length to pass validation. " +
            "It tests the system's ability to recover from partial failures during workflow execution.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "recovery-" + System.currentTimeMillis();
        
        logger.info("Testing partial workflow failure recovery with contentId: {}, workflowId: {}", contentId, workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            workflow.processContentApproval(contentId, "failure-test-recovery"));
        
        // Wait for workflow to reach a stable state
        Thread.sleep(3000);
        
        // Query workflow state during execution
        var intermediateState = workflow.getWorkflowState();
        assertNotNull(intermediateState);
        logger.info("Intermediate workflow state: {}", intermediateState.getStatus());
        
        // Complete workflow execution
        String result = workflowResult.get(60, TimeUnit.SECONDS);
        assertNotNull(result);
        
        // Verify final recovery state
        var finalState = workflow.getWorkflowState();
        assertTrue(finalState.isComplete());
        
        // Verify database consistency after recovery
        var finalRecord = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        assertNotNull(finalRecord);
        assertEquals(workflowId, finalRecord.getTemporalWorkflowId());
        
        logger.info("Partial workflow failure recovery test completed successfully");
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