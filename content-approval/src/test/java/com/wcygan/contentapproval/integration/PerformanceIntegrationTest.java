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
 * Performance integration tests for the Content Approval System.
 * Tests system behavior under load and measures performance characteristics.
 */
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class PerformanceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceIntegrationTest.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    DSLContext dsl;
    
    private static final String TEST_WORKFLOW_ID_PREFIX = "test-performance-";
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any test data from previous runs
        dsl.deleteFrom(CONTENT)
            .where(CONTENT.AUTHOR_ID.like("perf-test-%"))
            .execute();
    }
    
    @Test
    public void testHighVolumeWorkflowExecution() throws Exception {
        int numWorkflows = 20;
        List<CompletableFuture<String>> futures = new ArrayList<>();
        List<Long> contentIds = new ArrayList<>();
        List<String> workflowIds = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        logger.info("Starting high volume test with {} workflows", numWorkflows);
        
        // Create test content for each workflow
        for (int i = 0; i < numWorkflows; i++) {
            Long contentId = createTestContent("perf-test-volume-" + i, 
                "Performance Test Content " + i, 
                "This is performance test content with sufficient length to pass validation checks. " +
                "It contains enough text to meet the minimum requirements for content validation and " +
                "provides a realistic test scenario for high-volume processing.");
            contentIds.add(contentId);
        }
        
        // Start all workflows concurrently
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            for (int i = 0; i < numWorkflows; i++) {
                String workflowId = TEST_WORKFLOW_ID_PREFIX + "volume-" + i + "-" + System.currentTimeMillis();
                workflowIds.add(workflowId);
                
                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                        .build();
                
                ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                        ContentApprovalWorkflow.class, options);
                
                final int index = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 
                    workflow.processContentApproval(contentIds.get(index), "perf-test-volume-" + index), 
                    executor);
                
                futures.add(future);
            }
            
            // Wait for all workflows to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            allFutures.get(180, TimeUnit.SECONDS); // 3 minutes timeout
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double throughput = (double) numWorkflows / (totalTime / 1000.0);
            
            logger.info("High volume test completed in {}ms, throughput: {:.2f} workflows/second", 
                totalTime, throughput);
            
            // Verify all workflows completed successfully
            for (int i = 0; i < numWorkflows; i++) {
                String result = futures.get(i).get();
                assertNotNull(result, "Workflow " + i + " should have completed");
                assertEquals(workflowIds.get(i), result);
                
                // Verify database state
                var record = dsl.selectFrom(CONTENT)
                        .where(CONTENT.ID.eq(contentIds.get(i)))
                        .fetchOne();
                
                assertNotNull(record);
                assertEquals(workflowIds.get(i), record.getTemporalWorkflowId());
                assertTrue("PUBLISHED".equals(record.getStatus()) || "REJECTED".equals(record.getStatus()));
            }
            
            // Performance assertions
            assertTrue(throughput > 0.1, "Throughput should be at least 0.1 workflows/second");
            assertTrue(totalTime < 180000, "Total execution time should be under 3 minutes");
            
            logger.info("All {} workflows completed successfully with throughput {:.2f} workflows/second", 
                numWorkflows, throughput);
            
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
    
    @Test
    public void testMemoryUsageUnderLoad() throws Exception {
        int numWorkflows = 15;
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection and measure initial memory
        System.gc();
        Thread.sleep(1000);
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        logger.info("Starting memory usage test with {} workflows, initial memory: {} MB", 
            numWorkflows, initialMemory / (1024 * 1024));
        
        List<CompletableFuture<String>> futures = new ArrayList<>();
        List<Long> contentIds = new ArrayList<>();
        
        // Create test content
        for (int i = 0; i < numWorkflows; i++) {
            Long contentId = createTestContent("perf-test-memory-" + i, 
                "Memory Test Content " + i, 
                "This is memory test content designed to test memory usage patterns during " +
                "concurrent workflow execution. The content contains sufficient text to trigger " +
                "all validation and processing steps in the workflow system.");
            contentIds.add(contentId);
        }
        
        // Execute workflows
        for (int i = 0; i < numWorkflows; i++) {
            String workflowId = TEST_WORKFLOW_ID_PREFIX + "memory-" + i + "-" + System.currentTimeMillis();
            
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                    .build();
            
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, options);
            
            final int index = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 
                workflow.processContentApproval(contentIds.get(index), "perf-test-memory-" + index));
            
            futures.add(future);
        }
        
        // Monitor memory usage during execution
        long maxMemoryUsed = initialMemory;
        int completedWorkflows = 0;
        
        while (completedWorkflows < numWorkflows) {
            Thread.sleep(500);
            
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            maxMemoryUsed = Math.max(maxMemoryUsed, currentMemory);
            
            // Count completed workflows
            completedWorkflows = 0;
            for (CompletableFuture<String> future : futures) {
                if (future.isDone()) {
                    completedWorkflows++;
                }
            }
            
            logger.debug("Memory usage: {} MB, completed workflows: {}/{}", 
                currentMemory / (1024 * 1024), completedWorkflows, numWorkflows);
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);
        
        // Final memory measurement
        System.gc();
        Thread.sleep(1000);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long memoryIncrease = maxMemoryUsed - initialMemory;
        long memoryPerWorkflow = memoryIncrease / numWorkflows;
        
        logger.info("Memory test completed - Initial: {} MB, Max: {} MB, Final: {} MB", 
            initialMemory / (1024 * 1024), maxMemoryUsed / (1024 * 1024), finalMemory / (1024 * 1024));
        logger.info("Memory increase: {} MB, per workflow: {} KB", 
            memoryIncrease / (1024 * 1024), memoryPerWorkflow / 1024);
        
        // Memory usage assertions
        assertTrue(memoryPerWorkflow < 10 * 1024 * 1024, 
            "Memory per workflow should be less than 10MB, was: " + (memoryPerWorkflow / (1024 * 1024)) + "MB");
        assertTrue(finalMemory < initialMemory * 2, 
            "Final memory should not be more than 2x initial memory");
        
        // Verify all workflows completed
        for (CompletableFuture<String> future : futures) {
            assertNotNull(future.get(), "All workflows should complete successfully");
        }
        
        logger.info("Memory usage test passed - efficient memory usage maintained");
    }
    
    @Test
    public void testLatencyUnderNormalLoad() throws Exception {
        int numWorkflows = 10;
        List<Long> latencies = new ArrayList<>();
        
        logger.info("Starting latency test with {} workflows", numWorkflows);
        
        for (int i = 0; i < numWorkflows; i++) {
            Long contentId = createTestContent("perf-test-latency-" + i, 
                "Latency Test Content " + i, 
                "This is latency test content used to measure end-to-end workflow execution time. " +
                "The content is designed to be processed through all workflow stages to provide " +
                "accurate latency measurements for the complete approval process.");
            
            String workflowId = TEST_WORKFLOW_ID_PREFIX + "latency-" + i + "-" + System.currentTimeMillis();
            
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                    .build();
            
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, options);
            
            // Measure workflow execution time
            long startTime = System.currentTimeMillis();
            String result = workflow.processContentApproval(contentId, "perf-test-latency-" + i);
            long endTime = System.currentTimeMillis();
            
            long latency = endTime - startTime;
            latencies.add(latency);
            
            assertNotNull(result);
            assertEquals(workflowId, result);
            
            logger.info("Workflow {} completed in {}ms", i, latency);
        }
        
        // Calculate latency statistics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minLatency = latencies.stream().mapToLong(Long::longValue).min().orElse(0L);
        long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        // Calculate percentiles (simplified)
        latencies.sort(Long::compareTo);
        long p50 = latencies.get(numWorkflows * 50 / 100);
        long p95 = latencies.get(Math.min(numWorkflows * 95 / 100, numWorkflows - 1));
        
        logger.info("Latency statistics - Avg: {:.2f}ms, Min: {}ms, Max: {}ms, P50: {}ms, P95: {}ms", 
            avgLatency, minLatency, maxLatency, p50, p95);
        
        // Latency assertions
        assertTrue(avgLatency < 30000, "Average latency should be under 30 seconds");
        assertTrue(p95 < 60000, "95th percentile latency should be under 60 seconds");
        assertTrue(maxLatency < 120000, "Maximum latency should be under 2 minutes");
        
        logger.info("Latency test passed - acceptable response times maintained");
    }
    
    @Test
    public void testDatabaseConnectionPoolUnderLoad() throws Exception {
        int numConcurrentWorkflows = 25; // Higher than typical connection pool size
        List<CompletableFuture<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        logger.info("Starting database connection pool test with {} concurrent workflows", 
            numConcurrentWorkflows);
        
        // Create test content
        List<Long> contentIds = new ArrayList<>();
        for (int i = 0; i < numConcurrentWorkflows; i++) {
            Long contentId = createTestContent("perf-test-dbpool-" + i, 
                "DB Pool Test Content " + i, 
                "This content tests database connection pool behavior under high concurrent load. " +
                "Each workflow will perform multiple database operations including reads, writes, " +
                "and updates to test connection pool efficiency and resource management.");
            contentIds.add(contentId);
        }
        
        // Execute all workflows concurrently
        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentWorkflows);
        
        try {
            for (int i = 0; i < numConcurrentWorkflows; i++) {
                String workflowId = TEST_WORKFLOW_ID_PREFIX + "dbpool-" + i + "-" + System.currentTimeMillis();
                
                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
                        .build();
                
                ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                        ContentApprovalWorkflow.class, options);
                
                final int index = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        String result = workflow.processContentApproval(contentIds.get(index), 
                            "perf-test-dbpool-" + index);
                        successCount.incrementAndGet();
                        return result;
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.error("Workflow {} failed", index, e);
                        throw new RuntimeException(e);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // Wait for all workflows to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            allFutures.get(300, TimeUnit.SECONDS); // 5 minutes timeout
            
            // Verify results
            assertEquals(numConcurrentWorkflows, successCount.get(), 
                "All workflows should complete successfully");
            assertEquals(0, errorCount.get(), 
                "No workflows should fail due to database connection issues");
            
            // Verify all database operations completed correctly
            for (int i = 0; i < numConcurrentWorkflows; i++) {
                var record = dsl.selectFrom(CONTENT)
                        .where(CONTENT.ID.eq(contentIds.get(i)))
                        .fetchOne();
                
                assertNotNull(record, "Content record " + i + " should exist");
                assertNotNull(record.getTemporalWorkflowId(), 
                    "Workflow ID should be set for record " + i);
                assertTrue("PUBLISHED".equals(record.getStatus()) || "REJECTED".equals(record.getStatus()),
                    "Record " + i + " should have final status");
            }
            
            logger.info("Database connection pool test completed successfully - {} workflows, {} successes, {} errors", 
                numConcurrentWorkflows, successCount.get(), errorCount.get());
            
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
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