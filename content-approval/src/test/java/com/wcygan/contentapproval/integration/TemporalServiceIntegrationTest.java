package com.wcygan.contentapproval.integration;

import com.wcygan.contentapproval.IntegrationTestProfile;
import com.wcygan.contentapproval.config.TemporalWorkerConfig;
import com.wcygan.contentapproval.workflow.ContentApprovalState;
import com.wcygan.contentapproval.workflow.ContentApprovalWorkflow;
import com.wcygan.contentapproval.workflow.ContentStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.temporal.api.workflowservice.v1.ListClosedWorkflowExecutionsRequest;
import io.temporal.api.workflowservice.v1.ListClosedWorkflowExecutionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for real Temporal service integration.
 * Tests actual workflow execution against Temporal service with real activities.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class TemporalServiceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(TemporalServiceIntegrationTest.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    WorkflowServiceStubs workflowServiceStubs;
    
    private static final String TEST_WORKFLOW_ID_PREFIX = "test-temporal-integration-";
    
    @BeforeEach
    void setUp() {
        // Ensure we have a working connection to Temporal
        assertNotNull(workflowClient, "WorkflowClient should be injected");
        assertNotNull(workflowServiceStubs, "WorkflowServiceStubs should be injected");
    }
    
    @Test
    public void testWorkflowExecutionWithRealTemporalService() throws Exception {
        String workflowId = TEST_WORKFLOW_ID_PREFIX + System.currentTimeMillis();
        Long contentId = 100L;
        String authorId = "temporal-integration-author";
        
        logger.info("Starting workflow execution test with workflowId: {}", workflowId);
        
        // Create workflow options
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        // Start workflow
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Execute workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> {
            try {
                return workflow.processContentApproval(contentId, authorId);
            } catch (Exception e) {
                logger.error("Workflow execution failed", e);
                throw new RuntimeException(e);
            }
        });
        
        // Wait a moment for workflow to start and process
        Thread.sleep(2000);
        
        // Test query methods while workflow is running
        try {
            ContentApprovalState state = workflow.getWorkflowState();
            assertNotNull(state, "Workflow state should not be null");
            assertEquals(contentId, state.getContentId());
            assertEquals(authorId, state.getAuthorId());
            
            String status = workflow.getApprovalStatus();
            assertNotNull(status, "Approval status should not be null");
            logger.info("Current workflow status: {}", status);
            
            // Status should be either UNDER_REVIEW (if validation passed) or REJECTED (if validation failed)
            assertTrue(status.equals("UNDER_REVIEW") || status.equals("REJECTED"), 
                "Status should be UNDER_REVIEW or REJECTED, but was: " + status);
            
        } catch (Exception e) {
            logger.error("Error querying workflow state", e);
            throw e;
        }
        
        // Complete the workflow execution
        String result = workflowResult.get(30, TimeUnit.SECONDS);
        assertNotNull(result, "Workflow result should not be null");
        assertEquals(workflowId, result, "Workflow should return its own ID");
        
        logger.info("Workflow completed successfully with result: {}", result);
        
        // Verify final state
        ContentApprovalState finalState = workflow.getWorkflowState();
        assertNotNull(finalState);
        assertTrue(finalState.isComplete(), "Workflow should be marked as complete");
        
        // Final status should be either PUBLISHED, REJECTED (depending on validation result)
        ContentStatus finalStatus = finalState.getStatus();
        assertTrue(finalStatus == ContentStatus.PUBLISHED || finalStatus == ContentStatus.REJECTED,
            "Final status should be PUBLISHED or REJECTED, but was: " + finalStatus);
        
        logger.info("Final workflow state: {}", finalState);
    }
    
    @Test
    public void testWorkflowSignalHandling() throws Exception {
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "signal-" + System.currentTimeMillis();
        Long contentId = 101L;
        String authorId = "signal-test-author";
        
        logger.info("Starting signal handling test with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            workflow.processContentApproval(contentId, authorId));
        
        // Wait for workflow to reach review state
        Thread.sleep(3000);
        
        // Check if workflow is waiting for review (not auto-rejected)
        ContentApprovalState currentState = workflow.getWorkflowState();
        if (currentState.getStatus() == ContentStatus.UNDER_REVIEW) {
            // Send approval signal
            workflow.approve("integration-test-reviewer", "Approved via integration test");
            
            // Wait for signal processing
            Thread.sleep(2000);
            
            // Verify signal was processed
            ContentApprovalState approvedState = workflow.getWorkflowState();
            assertEquals(ContentStatus.PUBLISHED, approvedState.getStatus());
            assertEquals("integration-test-reviewer", approvedState.getCurrentReviewerId());
            assertEquals("Approved via integration test", approvedState.getApprovalComments());
            assertTrue(approvedState.isComplete());
            
            logger.info("Approval signal processed successfully");
        } else {
            logger.info("Workflow was auto-rejected due to validation failure, skipping signal test");
        }
        
        // Ensure workflow completes
        String result = workflowResult.get(30, TimeUnit.SECONDS);
        assertNotNull(result);
        
        logger.info("Signal handling test completed successfully");
    }
    
    @Test
    public void testWorkflowRejectionSignal() throws Exception {
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "reject-" + System.currentTimeMillis();
        Long contentId = 102L;
        String authorId = "reject-test-author";
        
        logger.info("Starting rejection signal test with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            workflow.processContentApproval(contentId, authorId));
        
        // Wait for workflow to reach review state
        Thread.sleep(3000);
        
        // Check if workflow is waiting for review
        ContentApprovalState currentState = workflow.getWorkflowState();
        if (currentState.getStatus() == ContentStatus.UNDER_REVIEW) {
            // Send rejection signal
            workflow.reject("integration-test-reviewer", "Rejected via integration test");
            
            // Wait for signal processing
            Thread.sleep(2000);
            
            // Verify rejection was processed
            ContentApprovalState rejectedState = workflow.getWorkflowState();
            assertEquals(ContentStatus.REJECTED, rejectedState.getStatus());
            assertEquals("integration-test-reviewer", rejectedState.getCurrentReviewerId());
            assertEquals("Rejected via integration test", rejectedState.getRejectionReason());
            assertTrue(rejectedState.isComplete());
            
            logger.info("Rejection signal processed successfully");
        } else {
            logger.info("Workflow was auto-rejected due to validation failure, signal test not applicable");
        }
        
        // Ensure workflow completes
        String result = workflowResult.get(30, TimeUnit.SECONDS);
        assertNotNull(result);
        
        logger.info("Rejection signal test completed successfully");
    }
    
    @Test
    public void testWorkflowRequestChangesSignal() throws Exception {
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "changes-" + System.currentTimeMillis();
        Long contentId = 103L;
        String authorId = "changes-test-author";
        
        logger.info("Starting request changes signal test with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            workflow.processContentApproval(contentId, authorId));
        
        // Wait for workflow to reach review state
        Thread.sleep(3000);
        
        // Check if workflow is waiting for review
        ContentApprovalState currentState = workflow.getWorkflowState();
        if (currentState.getStatus() == ContentStatus.UNDER_REVIEW) {
            // Send request changes signal
            workflow.requestChanges("integration-test-reviewer", "Please add more examples and fix formatting");
            
            // Wait for signal processing
            Thread.sleep(2000);
            
            // Verify changes request was processed
            ContentApprovalState changesState = workflow.getWorkflowState();
            assertEquals(ContentStatus.CHANGES_REQUESTED, changesState.getStatus());
            assertEquals("integration-test-reviewer", changesState.getCurrentReviewerId());
            assertEquals("Please add more examples and fix formatting", changesState.getChangeRequests());
            assertEquals(1, changesState.getRevisionCount());
            assertFalse(changesState.isComplete()); // Changes requested doesn't complete the workflow
            
            logger.info("Request changes signal processed successfully");
        } else {
            logger.info("Workflow was auto-rejected due to validation failure, signal test not applicable");
        }
        
        // Ensure workflow completes
        String result = workflowResult.get(30, TimeUnit.SECONDS);
        assertNotNull(result);
        
        logger.info("Request changes signal test completed successfully");
    }
    
    @Test
    public void testWorkflowHistoryQuerying() throws Exception {
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "history-" + System.currentTimeMillis();
        Long contentId = 104L;
        String authorId = "history-test-author";
        
        logger.info("Starting workflow history test with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Execute complete workflow
        String result = workflow.processContentApproval(contentId, authorId);
        assertNotNull(result);
        
        // Query workflow execution using WorkflowStub
        WorkflowStub stub = WorkflowStub.fromTyped(workflow);
        
        // Verify workflow execution details
        assertNotNull(stub.getExecution(), "Workflow execution should be available");
        assertEquals(workflowId, stub.getExecution().getWorkflowId());
        
        // Verify workflow result
        String workflowResult = stub.getResult(String.class);
        assertEquals(workflowId, workflowResult);
        
        logger.info("Workflow history querying completed successfully");
    }
    
    @Test
    public void testMultipleWorkflowsConcurrent() throws Exception {
        int numWorkflows = 3;
        CompletableFuture<String>[] futures = new CompletableFuture[numWorkflows];
        ContentApprovalWorkflow[] workflows = new ContentApprovalWorkflow[numWorkflows];
        
        logger.info("Starting concurrent workflows test with {} workflows", numWorkflows);
        
        // Start multiple workflows concurrently
        for (int i = 0; i < numWorkflows; i++) {
            String workflowId = TEST_WORKFLOW_ID_PREFIX + "concurrent-" + i + "-" + System.currentTimeMillis();
            Long contentId = 200L + i;
            String authorId = "concurrent-author-" + i;
            
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                    .build();
            
            workflows[i] = workflowClient.newWorkflowStub(ContentApprovalWorkflow.class, options);
            
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> 
                workflows[index].processContentApproval(contentId, authorId));
        }
        
        // Wait for all workflows to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
        allFutures.get(60, TimeUnit.SECONDS);
        
        // Verify all workflows completed successfully
        for (int i = 0; i < numWorkflows; i++) {
            String result = futures[i].get();
            assertNotNull(result, "Workflow " + i + " should have completed successfully");
            
            ContentApprovalState finalState = workflows[i].getWorkflowState();
            assertNotNull(finalState);
            assertTrue(finalState.isComplete(), "Workflow " + i + " should be marked as complete");
            
            logger.info("Concurrent workflow {} completed with status: {}", i, finalState.getStatus());
        }
        
        logger.info("All {} concurrent workflows completed successfully", numWorkflows);
    }
}