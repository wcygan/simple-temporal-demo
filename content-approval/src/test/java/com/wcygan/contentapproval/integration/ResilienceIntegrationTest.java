package com.wcygan.contentapproval.integration;

import com.wcygan.contentapproval.IntegrationTestProfile;
import com.wcygan.contentapproval.config.TemporalWorkerConfig;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import com.wcygan.contentapproval.workflow.ContentApprovalWorkflow;
import com.wcygan.contentapproval.workflow.ContentStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Resilience integration tests for the Content Approval System.
 * Tests system behavior under failure conditions and recovery scenarios.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class ResilienceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ResilienceIntegrationTest.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    DSLContext dsl;
    
    private static final String TEST_WORKFLOW_ID_PREFIX = "test-resilience-";
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any test data from previous runs
        dsl.deleteFrom(CONTENT)
            .where(CONTENT.AUTHOR_ID.like("resilience-test-%"))
            .execute();
    }
    
    @Test
    public void testWorkflowRecoveryAfterWorkerRestart() throws Exception {
        // This test simulates workflow continuation after worker restart
        Long contentId = createTestContent("resilience-test-restart", 
            "Worker Restart Test Content", 
            "This content tests the system's ability to recover workflows after worker restarts. " +
            "The workflow should be able to resume from its last checkpoint and complete successfully " +
            "even if the worker process is interrupted during execution.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "restart-" + System.currentTimeMillis();
        
        logger.info("Testing workflow recovery after worker restart with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            ContentApprovalWorkflow.processWithDefaults(workflow, contentId,  "resilience-test-restart"));
        
        // Wait a moment for workflow to start
        Thread.sleep(2000);
        
        // Verify workflow is running
        var initialState = workflow.getWorkflowState();
        assertNotNull(initialState);
        logger.info("Initial workflow state: {}", initialState.getStatus());
        
        // Simulate worker restart by creating a new workflow stub with the same ID
        // This tests Temporal's ability to reconnect to existing workflows
        ContentApprovalWorkflow reconnectedWorkflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, workflowId);
        
        // Query the reconnected workflow
        var reconnectedState = reconnectedWorkflow.getWorkflowState();
        assertNotNull(reconnectedState);
        assertEquals(initialState.getContentId(), reconnectedState.getContentId());
        assertEquals(initialState.getAuthorId(), reconnectedState.getAuthorId());
        
        logger.info("Successfully reconnected to workflow with state: {}", reconnectedState.getStatus());
        
        // Complete the workflow execution
        String result = workflowResult.get(120, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(workflowId, result);
        
        // Verify final state
        var finalState = reconnectedWorkflow.getWorkflowState();
        assertTrue(finalState.isComplete());
        
        logger.info("Workflow recovery test completed successfully");
    }
    
    @Test
    public void testHighFailureRateScenario() throws Exception {
        int numWorkflows = 15;
        int expectedSuccesses = (int) (numWorkflows * 0.6); // Expect ~60% success rate under stress
        
        List<CompletableFuture<String>> futures = new ArrayList<>();
        List<Long> contentIds = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        logger.info("Starting high failure rate test with {} workflows", numWorkflows);
        
        // Create test content with mix of valid and invalid content
        for (int i = 0; i < numWorkflows; i++) {
            String content;
            if (i % 3 == 0) {
                // Some content that will fail validation
                content = "Short"; 
            } else {
                // Valid content
                content = "This is comprehensive test content with sufficient length to pass validation checks. " +
                         "It contains enough text to meet the minimum requirements and provides realistic test scenarios.";
            }
            
            Long contentId = createTestContent("resilience-test-failure-" + i, 
                "Failure Rate Test " + i, content);
            contentIds.add(contentId);
        }
        
        // Execute workflows with aggressive timeouts to increase failure probability
        ExecutorService executor = Executors.newFixedThreadPool(8);
        
        try {
            for (int i = 0; i < numWorkflows; i++) {
                String workflowId = TEST_WORKFLOW_ID_PREFIX + "failure-" + i + "-" + System.currentTimeMillis();
                
                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(3)) // Shorter timeout
                        .build();
                
                ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                        ContentApprovalWorkflow.class, options);
                
                final int index = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        String result = ContentApprovalWorkflow.processWithDefaults(workflow, contentIds.get(index), 
                            "resilience-test-failure-" + index);
                        successCount.incrementAndGet();
                        return result;
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        logger.debug("Expected failure for workflow {}: {}", index, e.getMessage());
                        return null; // Expected failure
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // Wait for all workflows to complete (either success or failure)
            for (CompletableFuture<String> future : futures) {
                try {
                    future.get(180, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Expected for some workflows
                    logger.debug("Workflow completed with exception (expected): {}", e.getMessage());
                }
            }
            
            int totalCompleted = successCount.get() + failureCount.get();
            double successRate = (double) successCount.get() / totalCompleted;
            
            logger.info("High failure rate test completed - Total: {}, Successes: {}, Failures: {}, Success Rate: {:.2f}%", 
                totalCompleted, successCount.get(), failureCount.get(), successRate * 100);
            
            // System should handle failures gracefully
            assertTrue(successCount.get() > 0, "At least some workflows should succeed");
            assertTrue(totalCompleted >= numWorkflows * 0.8, "Most workflows should complete (success or controlled failure)");
            
            // Verify database consistency for successful workflows
            int dbSuccessCount = dsl.selectCount()
                    .from(CONTENT)
                    .where(CONTENT.AUTHOR_ID.like("resilience-test-failure-%")
                            .and(CONTENT.TEMPORAL_WORKFLOW_ID.isNotNull()))
                    .fetchOne(0, int.class);
            
            logger.info("Database records with workflow IDs: {}", dbSuccessCount);
            assertTrue(dbSuccessCount > 0, "Some workflows should have updated database successfully");
            
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
    
    @Test
    public void testWorkflowTimeoutHandling() throws Exception {
        Long contentId = createTestContent("resilience-test-timeout", 
            "Timeout Test Content", 
            "This content tests the system's handling of workflow timeouts and proper cleanup procedures.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "timeout-" + System.currentTimeMillis();
        
        logger.info("Testing workflow timeout handling with workflowId: {}", workflowId);
        
        // Set a very short timeout to force timeout scenario
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30)) // Very short timeout
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        try {
            // This should complete within timeout or handle timeout gracefully
            String result = ContentApprovalWorkflow.processWithDefaults(workflow, contentId,  "resilience-test-timeout");
            
            // If it completes successfully, that's fine too
            assertNotNull(result);
            logger.info("Workflow completed within timeout period: {}", result);
            
        } catch (Exception e) {
            // Timeout is expected, verify it's handled gracefully
            logger.info("Expected timeout occurred: {}", e.getMessage());
            
            // Verify database state is consistent even after timeout
            var record = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            assertNotNull(record);
            // Record should exist and either have a workflow ID or be in original state
            assertTrue(record.getTemporalWorkflowId() == null || 
                      record.getTemporalWorkflowId().equals(workflowId));
        }
        
        logger.info("Workflow timeout handling test completed");
    }
    
    @Test
    public void testConcurrentSignalHandlingResilience() throws Exception {
        Long contentId = createTestContent("resilience-test-signals", 
            "Signal Resilience Test", 
            "This content tests the system's resilience when handling multiple concurrent signals " +
            "and ensures proper signal ordering and processing under high load conditions.");
        
        String workflowId = TEST_WORKFLOW_ID_PREFIX + "signals-" + System.currentTimeMillis();
        
        logger.info("Testing concurrent signal handling resilience with workflowId: {}", workflowId);
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build();
        
        ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                ContentApprovalWorkflow.class, options);
        
        // Start workflow asynchronously
        CompletableFuture<String> workflowResult = CompletableFuture.supplyAsync(() -> 
            ContentApprovalWorkflow.processWithDefaults(workflow, contentId,  "resilience-test-signals"));
        
        // Wait for workflow to reach review state
        Thread.sleep(3000);
        
        // Check if workflow is in review state
        var currentState = workflow.getWorkflowState();
        if (currentState.getStatus() == ContentStatus.UNDER_REVIEW) {
            // Send multiple concurrent signals to test resilience
            ExecutorService signalExecutor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<Void>> signalFutures = new ArrayList<>();
            
            try {
                // Send multiple approval signals concurrently (only first should be processed)
                for (int i = 0; i < 3; i++) {
                    final int index = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            workflow.approve("resilience-reviewer-" + index, "Concurrent approval " + index);
                            logger.debug("Sent approval signal {}", index);
                        } catch (Exception e) {
                            logger.debug("Approval signal {} failed (expected): {}", index, e.getMessage());
                        }
                    }, signalExecutor);
                    signalFutures.add(future);
                }
                
                // Also send rejection signals (should be ignored since approval came first)
                for (int i = 0; i < 2; i++) {
                    final int index = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            workflow.reject("resilience-reviewer-reject-" + index, "Concurrent rejection " + index);
                            logger.debug("Sent rejection signal {}", index);
                        } catch (Exception e) {
                            logger.debug("Rejection signal {} failed (expected): {}", index, e.getMessage());
                        }
                    }, signalExecutor);
                    signalFutures.add(future);
                }
                
                // Wait for all signals to be sent
                CompletableFuture.allOf(signalFutures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
                
                logger.info("All concurrent signals sent");
                
            } finally {
                signalExecutor.shutdown();
                if (!signalExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    signalExecutor.shutdownNow();
                }
            }
            
            // Wait for workflow to process signals
            Thread.sleep(2000);
            
            // Verify final state - should be approved despite multiple conflicting signals
            var finalState = workflow.getWorkflowState();
            assertEquals(ContentStatus.PUBLISHED, finalState.getStatus());
            assertTrue(finalState.getCurrentReviewerId().startsWith("resilience-reviewer-"));
            assertTrue(finalState.isComplete());
            
            logger.info("Concurrent signals processed correctly, final state: {}", finalState.getStatus());
        } else {
            logger.info("Workflow auto-rejected due to validation, skipping signal test");
        }
        
        // Ensure workflow completes
        String result = workflowResult.get(60, TimeUnit.SECONDS);
        assertNotNull(result);
        
        logger.info("Concurrent signal handling resilience test completed successfully");
    }
    
    @Test
    public void testResourceExhaustionRecovery() throws Exception {
        int numWorkflows = 30; // High number to stress system resources
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        logger.info("Starting resource exhaustion recovery test with {} workflows", numWorkflows);
        
        // Create test content
        List<Long> contentIds = new ArrayList<>();
        for (int i = 0; i < numWorkflows; i++) {
            Long contentId = createTestContent("resilience-test-resource-" + i, 
                "Resource Test " + i, 
                "This content tests system behavior under resource exhaustion conditions including " +
                "database connection limits, memory pressure, and concurrent execution limits.");
            contentIds.add(contentId);
        }
        
        // Execute workflows with limited thread pool to force resource contention
        ExecutorService limitedExecutor = Executors.newFixedThreadPool(5); // Intentionally small
        
        try {
            for (int i = 0; i < numWorkflows; i++) {
                String workflowId = TEST_WORKFLOW_ID_PREFIX + "resource-" + i + "-" + System.currentTimeMillis();
                
                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(8))
                        .build();
                
                ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                        ContentApprovalWorkflow.class, options);
                
                final int index = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        String result = ContentApprovalWorkflow.processWithDefaults(workflow, contentIds.get(index), 
                            "resilience-test-resource-" + index);
                        completedCount.incrementAndGet();
                        return result;
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.debug("Workflow {} failed under resource pressure: {}", index, e.getMessage());
                        throw new RuntimeException(e);
                    }
                }, limitedExecutor);
                
                futures.add(future);
                
                // Add small delay to prevent overwhelming the system immediately
                if (i % 5 == 0) {
                    Thread.sleep(100);
                }
            }
            
            // Monitor progress and allow for gradual completion
            int previousCompleted = 0;
            int stableCount = 0;
            
            while (completedCount.get() + errorCount.get() < numWorkflows && stableCount < 10) {
                Thread.sleep(5000);
                int currentCompleted = completedCount.get() + errorCount.get();
                
                if (currentCompleted == previousCompleted) {
                    stableCount++;
                } else {
                    stableCount = 0;
                    previousCompleted = currentCompleted;
                }
                
                logger.info("Progress: {} completed, {} errors, {} total", 
                    completedCount.get(), errorCount.get(), currentCompleted);
            }
            
            // Final wait for remaining workflows
            for (CompletableFuture<String> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Some failures are expected under resource pressure
                    logger.debug("Workflow completed with exception under resource pressure: {}", e.getMessage());
                }
            }
            
            int totalProcessed = completedCount.get() + errorCount.get();
            double completionRate = (double) completedCount.get() / totalProcessed;
            
            logger.info("Resource exhaustion test completed - Processed: {}/{}, Success Rate: {:.2f}%", 
                totalProcessed, numWorkflows, completionRate * 100);
            
            // System should handle resource pressure gracefully
            assertTrue(completedCount.get() > 0, "Some workflows should complete successfully under pressure");
            assertTrue(totalProcessed >= numWorkflows * 0.7, "Most workflows should be processed despite resource pressure");
            
            // Verify database consistency
            int dbRecordCount = dsl.selectCount()
                    .from(CONTENT)
                    .where(CONTENT.AUTHOR_ID.like("resilience-test-resource-%"))
                    .fetchOne(0, int.class);
            
            assertEquals(numWorkflows, dbRecordCount, "All content records should exist in database");
            
        } finally {
            limitedExecutor.shutdown();
            if (!limitedExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                limitedExecutor.shutdownNow();
            }
        }
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