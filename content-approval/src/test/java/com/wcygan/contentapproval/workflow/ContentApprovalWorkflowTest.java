package com.wcygan.contentapproval.workflow;

import com.wcygan.contentapproval.activity.ContentPersistenceActivity;
import com.wcygan.contentapproval.activity.ContentValidationActivity;
import com.wcygan.contentapproval.activity.NotificationActivity;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContentApprovalWorkflow using TestWorkflowEnvironment.
 * Tests workflow logic with mocked activities for fast, reliable testing.
 */
public class ContentApprovalWorkflowTest {
    
    private TestWorkflowEnvironment testEnv;
    private static final String TASK_QUEUE = "test-content-approval-queue";
    
    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
    }
    
    @AfterEach
    void tearDown() {
        testEnv.close();
    }
    
    @Test
    public void testSuccessfulApprovalFlow() {
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        
        // Mock activities
        ContentValidationActivity mockValidation = mock(ContentValidationActivity.class);
        ContentPersistenceActivity mockPersistence = mock(ContentPersistenceActivity.class);
        NotificationActivity mockNotification = mock(NotificationActivity.class);
        
        // Configure mock behavior for successful flow
        when(mockValidation.validateContent(anyLong())).thenReturn(true);
        doNothing().when(mockPersistence).linkContentToWorkflow(anyLong(), anyString());
        doNothing().when(mockPersistence).updateContentStatus(anyLong(), anyString());
        doNothing().when(mockPersistence).publishContent(anyLong());
        doNothing().when(mockNotification).notifyReviewers(anyLong(), anyString(), anyString());
        doNothing().when(mockNotification).notifyAuthor(anyString(), anyString(), anyString());
        
        // Register workflow and mocked activities
        worker.registerWorkflowImplementationTypes(ContentApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(mockValidation, mockPersistence, mockNotification);
        testEnv.start();
        
        // Create workflow stub
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(1))
                .build();
        
        ContentApprovalWorkflow workflow = testEnv.getWorkflowClient()
                .newWorkflowStub(ContentApprovalWorkflow.class, options);
        
        // Start workflow in background
        CompletableFuture<String> future = io.temporal.client.WorkflowClient.execute(
            () -> workflow.processContentApproval(1L, "testAuthor"));
        
        // Wait for workflow to reach review state
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Verify initial state
        ContentApprovalState state = workflow.getWorkflowState();
        assertNotNull(state);
        assertEquals(ContentStatus.UNDER_REVIEW, state.getStatus());
        assertEquals(1L, state.getContentId());
        assertEquals("testAuthor", state.getAuthorId());
        assertFalse(state.isComplete());
        
        // Send approval signal
        workflow.approve("reviewer1", "Looks good!");
        
        // Wait for workflow to complete
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Verify final state
        state = workflow.getWorkflowState();
        assertEquals(ContentStatus.PUBLISHED, state.getStatus());
        assertEquals("reviewer1", state.getCurrentReviewerId());
        assertEquals("Looks good!", state.getApprovalComments());
        assertTrue(state.isComplete());
        
        // Verify activity calls
        verify(mockValidation).validateContent(1L);
        verify(mockPersistence).linkContentToWorkflow(eq(1L), anyString());
        verify(mockPersistence).updateContentStatus(1L, "UNDER_REVIEW");
        verify(mockPersistence).updateContentStatus(1L, "APPROVED");
        verify(mockPersistence).publishContent(1L);
        verify(mockNotification).notifyReviewers(eq(1L), eq("testAuthor"), anyString());
        verify(mockNotification, atLeast(2)).notifyAuthor(eq("testAuthor"), anyString(), anyString());
    }
    
    @Test
    public void testRejectionFlow() {
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        
        // Mock activities
        ContentValidationActivity mockValidation = mock(ContentValidationActivity.class);
        ContentPersistenceActivity mockPersistence = mock(ContentPersistenceActivity.class);
        NotificationActivity mockNotification = mock(NotificationActivity.class);
        
        // Configure mock behavior
        when(mockValidation.validateContent(anyLong())).thenReturn(true);
        doNothing().when(mockPersistence).linkContentToWorkflow(anyLong(), anyString());
        doNothing().when(mockPersistence).updateContentStatus(anyLong(), anyString());
        doNothing().when(mockNotification).notifyReviewers(anyLong(), anyString(), anyString());
        doNothing().when(mockNotification).notifyAuthor(anyString(), anyString(), anyString());
        
        // Register workflow and mocked activities
        worker.registerWorkflowImplementationTypes(ContentApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(mockValidation, mockPersistence, mockNotification);
        testEnv.start();
        
        // Create workflow stub
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(1))
                .build();
        
        ContentApprovalWorkflow workflow = testEnv.getWorkflowClient()
                .newWorkflowStub(ContentApprovalWorkflow.class, options);
        
        // Start workflow
        CompletableFuture<String> future2 = io.temporal.client.WorkflowClient.execute(
            () -> workflow.processContentApproval(2L, "testAuthor2"));
        
        // Wait for workflow to reach review state
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Send rejection signal
        workflow.reject("reviewer2", "Content quality is insufficient");
        
        // Wait for workflow to complete
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Verify final state
        ContentApprovalState state = workflow.getWorkflowState();
        assertEquals(ContentStatus.REJECTED, state.getStatus());
        assertEquals("reviewer2", state.getCurrentReviewerId());
        assertEquals("Content quality is insufficient", state.getRejectionReason());
        assertTrue(state.isComplete());
        
        // Verify rejection was persisted
        verify(mockPersistence).updateContentStatus(2L, "REJECTED");
        verify(mockNotification).notifyAuthor(eq("testAuthor2"), eq("Content Rejected"), anyString());
    }
    
    @Test
    public void testValidationFailureFlow() {
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        
        // Mock activities
        ContentValidationActivity mockValidation = mock(ContentValidationActivity.class);
        ContentPersistenceActivity mockPersistence = mock(ContentPersistenceActivity.class);
        NotificationActivity mockNotification = mock(NotificationActivity.class);
        
        // Configure mock behavior for validation failure
        when(mockValidation.validateContent(anyLong())).thenReturn(false);
        doNothing().when(mockPersistence).linkContentToWorkflow(anyLong(), anyString());
        doNothing().when(mockPersistence).updateContentStatus(anyLong(), anyString());
        doNothing().when(mockNotification).notifyAuthor(anyString(), anyString(), anyString());
        
        // Register workflow and mocked activities
        worker.registerWorkflowImplementationTypes(ContentApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(mockValidation, mockPersistence, mockNotification);
        testEnv.start();
        
        // Create workflow stub
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(1))
                .build();
        
        ContentApprovalWorkflow workflow = testEnv.getWorkflowClient()
                .newWorkflowStub(ContentApprovalWorkflow.class, options);
        
        // Start and wait for workflow to complete
        String result = workflow.processContentApproval(4L, "testAuthor4");
        
        // Verify auto-rejection due to validation failure
        ContentApprovalState state = workflow.getWorkflowState();
        assertEquals(ContentStatus.REJECTED, state.getStatus());
        assertEquals("system", state.getCurrentReviewerId());
        assertEquals("Content failed automated validation", state.getRejectionReason());
        assertTrue(state.isComplete());
        
        // Verify validation was called but no reviewer notification
        verify(mockValidation).validateContent(4L);
        verify(mockPersistence).updateContentStatus(4L, "REJECTED");
        verify(mockNotification).notifyAuthor(eq("testAuthor4"), eq("Content Rejected"), anyString());
        verify(mockNotification, never()).notifyReviewers(anyLong(), anyString(), anyString());
    }
    
    @Test
    public void testQueryMethods() {
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        
        // Mock activities
        ContentValidationActivity mockValidation = mock(ContentValidationActivity.class);
        ContentPersistenceActivity mockPersistence = mock(ContentPersistenceActivity.class);
        NotificationActivity mockNotification = mock(NotificationActivity.class);
        
        when(mockValidation.validateContent(anyLong())).thenReturn(true);
        doNothing().when(mockPersistence).linkContentToWorkflow(anyLong(), anyString());
        doNothing().when(mockPersistence).updateContentStatus(anyLong(), anyString());
        doNothing().when(mockNotification).notifyReviewers(anyLong(), anyString(), anyString());
        
        worker.registerWorkflowImplementationTypes(ContentApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(mockValidation, mockPersistence, mockNotification);
        testEnv.start();
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(1))
                .build();
        
        ContentApprovalWorkflow workflow = testEnv.getWorkflowClient()
                .newWorkflowStub(ContentApprovalWorkflow.class, options);
        
        // Start workflow
        CompletableFuture<String> future3 = io.temporal.client.WorkflowClient.execute(
            () -> workflow.processContentApproval(6L, "testAuthor6"));
        
        // Wait for workflow to reach review state
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Test query methods
        assertEquals("UNDER_REVIEW", workflow.getApprovalStatus());
        assertFalse(workflow.isComplete());
        
        ContentApprovalState state = workflow.getWorkflowState();
        assertNotNull(state);
        assertEquals(6L, state.getContentId());
        assertEquals("testAuthor6", state.getAuthorId());
        assertEquals(ContentStatus.UNDER_REVIEW, state.getStatus());
    }
}