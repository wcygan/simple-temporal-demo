package com.wcygan.contentapproval.workflow;

import com.wcygan.contentapproval.activity.ContentPersistenceActivity;
import com.wcygan.contentapproval.activity.ContentValidationActivity;
import com.wcygan.contentapproval.activity.NotificationActivity;
import com.wcygan.contentapproval.dto.WorkflowConfiguration;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Implementation of the ContentApprovalWorkflow.
 * This workflow orchestrates the content approval process including validation,
 * persistence, and notifications.
 */
public class ContentApprovalWorkflowImpl implements ContentApprovalWorkflow {
    
    private static final Logger logger = Workflow.getLogger(ContentApprovalWorkflowImpl.class);
    
    // Activity stubs with configured timeouts and retry policies
    private final ContentValidationActivity validationActivity;
    private final ContentPersistenceActivity persistenceActivity;
    private final NotificationActivity notificationActivity;
    
    // Workflow state
    private ContentApprovalState workflowState;
    private volatile boolean approvalReceived = false;
    private volatile boolean rejectionReceived = false;
    private volatile boolean changesRequested = false;
    
    public ContentApprovalWorkflowImpl() {
        // Configure activity options with appropriate timeouts and retry policies
        ActivityOptions shortActivityOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(5))
                .setRetryOptions(io.temporal.common.RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .setBackoffCoefficient(2.0)
                        .build())
                .build();
        
        ActivityOptions longActivityOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(30))
                .setRetryOptions(io.temporal.common.RetryOptions.newBuilder()
                        .setMaximumAttempts(5)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .setBackoffCoefficient(2.0)
                        .build())
                .build();
        
        // Create activity stubs
        this.validationActivity = Workflow.newActivityStub(ContentValidationActivity.class, shortActivityOptions);
        this.persistenceActivity = Workflow.newActivityStub(ContentPersistenceActivity.class, shortActivityOptions);
        this.notificationActivity = Workflow.newActivityStub(NotificationActivity.class, longActivityOptions);
    }
    
    @Override
    public String processContentApproval(Long contentId, String authorId, WorkflowConfiguration configuration) {
        logger.info("Starting content approval workflow for contentId: {}, authorId: {}", contentId, authorId);
        
        // Initialize workflow state
        workflowState = new ContentApprovalState(contentId, authorId);
        String workflowId = Workflow.getInfo().getWorkflowId();
        
        try {
            // Step 1: Link content to workflow in database
            persistenceActivity.linkContentToWorkflow(contentId, workflowId);
            logger.info("Linked content {} to workflow {}", contentId, workflowId);
            
            // Step 2: Perform initial content validation
            logger.info("Performing content validation for contentId: {}", contentId);
            boolean isValid = validationActivity.validateContent(contentId);
            
            if (!isValid) {
                // Auto-reject if validation fails
                workflowState.reject("system", "Content failed automated validation");
                persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
                notificationActivity.notifyAuthor(authorId, "Content Rejected", 
                    "Your content failed automated validation and has been rejected.");
                logger.info("Content {} auto-rejected due to validation failure", contentId);
                return workflowId;
            }
            
            // Step 3: Move to under review
            workflowState.startReview();
            persistenceActivity.updateContentStatus(contentId, ContentStatus.UNDER_REVIEW.getDatabaseValue());
            
            // Step 4: Notify reviewers that content is ready for review
            notificationActivity.notifyReviewers(contentId, authorId, "New content ready for review");
            logger.info("Content {} moved to under review", contentId);
            
            // Step 5: Wait for approval decision with timeout
            Duration reviewTimeout = configuration.reviewTimeout();
            logger.info("Waiting for approval decision with timeout of {} seconds", reviewTimeout.getSeconds());
            
            boolean decisionReceived = Workflow.await(reviewTimeout, 
                () -> approvalReceived || rejectionReceived || changesRequested);
            
            if (!decisionReceived) {
                // Auto-reject after timeout
                workflowState.reject("system", "Review timeout exceeded");
                persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
                notificationActivity.notifyAuthor(authorId, "Content Review Timeout", 
                    "Your content review has timed out and has been automatically rejected.");
                logger.info("Content {} auto-rejected due to review timeout", contentId);
                return workflowId;
            }
            
            // Step 6: Handle the decision
            if (approvalReceived) {
                // Move to approved status
                persistenceActivity.updateContentStatus(contentId, ContentStatus.APPROVED.getDatabaseValue());
                
                // Notify author of approval
                notificationActivity.notifyAuthor(authorId, "Content Approved", 
                    "Your content has been approved" + 
                    (workflowState.getApprovalComments() != null ? 
                        ": " + workflowState.getApprovalComments() : "."));
                
                // Auto-publish approved content
                persistenceActivity.publishContent(contentId);
                workflowState.setStatus(ContentStatus.PUBLISHED);
                
                // Notify author of publication
                notificationActivity.notifyAuthor(authorId, "Content Published", 
                    "Your approved content has been published.");
                
                logger.info("Content {} approved and published", contentId);
                
            } else if (rejectionReceived) {
                // Content already marked as rejected in signal handler
                notificationActivity.notifyAuthor(authorId, "Content Rejected", 
                    "Your content has been rejected" + 
                    (workflowState.getRejectionReason() != null ? 
                        ": " + workflowState.getRejectionReason() : "."));
                
                logger.info("Content {} rejected", contentId);
                
            } else if (changesRequested) {
                // Notify author of requested changes
                notificationActivity.notifyAuthor(authorId, "Changes Requested", 
                    "Changes have been requested for your content" + 
                    (workflowState.getChangeRequests() != null ? 
                        ": " + workflowState.getChangeRequests() : "."));
                
                logger.info("Changes requested for content {}", contentId);
                
                // Could extend workflow to wait for resubmission, but for now we'll end here
                // In a more complex implementation, this could loop back to validation
            }
            
            workflowState.setComplete(true);
            logger.info("Content approval workflow completed for contentId: {}", contentId);
            
        } catch (Exception e) {
            logger.error("Error in content approval workflow for contentId: {}", contentId, e);
            // Compensate by marking as failed
            workflowState.reject("system", "Workflow execution failed: " + e.getMessage());
            persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
            throw e;
        }
        
        return workflowId;
    }
    
    @Override
    public void approve(String approverId, String comments) {
        logger.info("Approval signal received from {}: {}", approverId, comments);
        
        if (workflowState != null && workflowState.getStatus().canBeApproved()) {
            workflowState.approve(approverId, comments);
            approvalReceived = true;
            logger.info("Content approval processed for reviewer: {}", approverId);
        } else {
            logger.warn("Approval signal ignored - content not in approvable state: {}", 
                workflowState != null ? workflowState.getStatus() : "null");
        }
    }
    
    @Override
    public void reject(String reviewerId, String reason) {
        logger.info("Rejection signal received from {}: {}", reviewerId, reason);
        
        if (workflowState != null && workflowState.getStatus().canBeRejected()) {
            workflowState.reject(reviewerId, reason);
            // Update database status immediately
            persistenceActivity.updateContentStatus(workflowState.getContentId(), 
                ContentStatus.REJECTED.getDatabaseValue());
            rejectionReceived = true;
            logger.info("Content rejection processed for reviewer: {}", reviewerId);
        } else {
            logger.warn("Rejection signal ignored - content not in rejectable state: {}", 
                workflowState != null ? workflowState.getStatus() : "null");
        }
    }
    
    @Override
    public void requestChanges(String reviewerId, String changeRequests) {
        logger.info("Changes requested signal received from {}: {}", reviewerId, changeRequests);
        
        if (workflowState != null && workflowState.getStatus().canRequestChanges()) {
            workflowState.requestChanges(reviewerId, changeRequests);
            // Update database status immediately
            persistenceActivity.updateContentStatus(workflowState.getContentId(), 
                ContentStatus.CHANGES_REQUESTED.getDatabaseValue());
            changesRequested = true;
            logger.info("Changes requested processed for reviewer: {}", reviewerId);
        } else {
            logger.warn("Changes request ignored - content not in changeable state: {}", 
                workflowState != null ? workflowState.getStatus() : "null");
        }
    }
    
    @Override
    public ContentApprovalState getWorkflowState() {
        return workflowState;
    }
    
    @Override
    public String getApprovalStatus() {
        return workflowState != null ? workflowState.getStatus().toString() : "UNKNOWN";
    }
    
    @Override
    public boolean isComplete() {
        return workflowState != null && workflowState.isComplete();
    }
}