package com.wcygan.contentapproval.workflow;

import com.wcygan.contentapproval.activity.ContentPersistenceActivity;
import com.wcygan.contentapproval.activity.ContentValidationActivity;
import com.wcygan.contentapproval.activity.NotificationActivity;
import com.wcygan.contentapproval.dto.WorkflowConfiguration;
import com.wcygan.contentapproval.logging.LogContext;
import com.wcygan.contentapproval.logging.OperationLogger;
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
    private final OperationLogger opLogger = new OperationLogger(logger);
    
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
        String workflowId = Workflow.getInfo().getWorkflowId();
        LogContext context = LogContext.withContentAndWorkflow(contentId, workflowId)
            .userId(authorId)
            .operation("processContentApproval")
            .field("validationEnabled", configuration.validationEnabled())
            .field("autoPublishEnabled", configuration.autoPublishEnabled())
            .field("reviewTimeoutSeconds", configuration.getReviewTimeoutSeconds());
        
        opLogger.operationStarted("Content approval workflow", context);
        
        // Initialize workflow state
        workflowState = new ContentApprovalState(contentId, authorId);
        
        try {
            // Step 1: Link content to workflow in database
            persistenceActivity.linkContentToWorkflow(contentId, workflowId);
            opLogger.operationCompleted("Content-workflow linking", context);
            
            // Step 2: Perform initial content validation
            opLogger.operationStarted("Content validation", context);
            boolean isValid = validationActivity.validateContent(contentId);
            
            if (!isValid) {
                // Auto-reject if validation fails
                workflowState.reject("system", "Content failed automated validation");
                persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
                notificationActivity.notifyAuthor(authorId, "Content Rejected", 
                    "Your content failed automated validation and has been rejected.");
                
                opLogger.auditEvent("Content auto-rejected due to validation failure", 
                    context.status("REJECTED").field("rejectedBy", "system"));
                opLogger.operationCompleted("Content approval workflow", context);
                return workflowId;
            }
            
            // Step 3: Move to under review
            workflowState.startReview();
            persistenceActivity.updateContentStatus(contentId, ContentStatus.UNDER_REVIEW.getDatabaseValue());
            
            // Step 4: Notify reviewers that content is ready for review
            notificationActivity.notifyReviewers(contentId, authorId, "New content ready for review");
            opLogger.auditEvent("Content moved to under review", context.status("UNDER_REVIEW"));
            
            // Step 5: Wait for approval decision with timeout
            Duration reviewTimeout = configuration.reviewTimeout();
            opLogger.operationStarted("Review decision wait", 
                context.field("timeoutSeconds", reviewTimeout.getSeconds()));
            
            boolean decisionReceived = Workflow.await(reviewTimeout, 
                () -> approvalReceived || rejectionReceived || changesRequested);
            
            if (!decisionReceived) {
                // Auto-reject after timeout
                workflowState.reject("system", "Review timeout exceeded");
                persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
                notificationActivity.notifyAuthor(authorId, "Content Review Timeout", 
                    "Your content review has timed out and has been automatically rejected.");
                opLogger.auditEvent("Content auto-rejected due to timeout", 
                    context.status("REJECTED").field("rejectedBy", "system"));
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
                
                opLogger.auditEvent("Content approved and published", 
                    context.status("PUBLISHED").field("approvedBy", workflowState.getCurrentReviewerId()));
                
            } else if (rejectionReceived) {
                // Content already marked as rejected in signal handler
                notificationActivity.notifyAuthor(authorId, "Content Rejected", 
                    "Your content has been rejected" + 
                    (workflowState.getRejectionReason() != null ? 
                        ": " + workflowState.getRejectionReason() : "."));
                
                opLogger.auditEvent("Content rejection completed", 
                    context.status("REJECTED").field("rejectedBy", workflowState.getCurrentReviewerId()));
                
            } else if (changesRequested) {
                // Notify author of requested changes
                notificationActivity.notifyAuthor(authorId, "Changes Requested", 
                    "Changes have been requested for your content" + 
                    (workflowState.getChangeRequests() != null ? 
                        ": " + workflowState.getChangeRequests() : "."));
                
                opLogger.auditEvent("Changes requested workflow completed", 
                    context.status("CHANGES_REQUESTED").field("requestedBy", workflowState.getCurrentReviewerId()));
                
                // Could extend workflow to wait for resubmission, but for now we'll end here
                // In a more complex implementation, this could loop back to validation
            }
            
            workflowState.setComplete(true);
            opLogger.operationCompleted("Content approval workflow", context);
            
        } catch (Exception e) {
            opLogger.operationFailed("Content approval workflow", context, 
                "Workflow execution failed", e);
            // Compensate by marking as failed
            workflowState.reject("system", "Workflow execution failed: " + e.getMessage());
            persistenceActivity.updateContentStatus(contentId, ContentStatus.REJECTED.getDatabaseValue());
            throw e;
        }
        
        return workflowId;
    }
    
    
    @Override
    public void approve(String approverId, String comments) {
        LogContext context = LogContext.withContentAndWorkflow(
                workflowState != null ? workflowState.getContentId() : null, 
                Workflow.getInfo().getWorkflowId())
            .userId(approverId)
            .operation("approve")
            .field("hasComments", comments != null && !comments.trim().isEmpty());
        
        opLogger.operationStarted("Content approval signal", context);
        
        if (workflowState != null && workflowState.getStatus().canBeApproved()) {
            workflowState.approve(approverId, comments);
            approvalReceived = true;
            
            opLogger.auditEvent("Content approved by reviewer", 
                context.status("APPROVED").field("comments", comments != null ? "present" : "none"));
            opLogger.operationCompleted("Content approval signal", context);
        } else {
            String currentState = workflowState != null ? workflowState.getStatus().toString() : "null";
            opLogger.businessRuleViolation("Approval state transition", 
                context.status(currentState), "Content not in approvable state");
        }
    }
    
    @Override
    public void reject(String reviewerId, String reason) {
        LogContext context = LogContext.withContentAndWorkflow(
                workflowState != null ? workflowState.getContentId() : null, 
                Workflow.getInfo().getWorkflowId())
            .userId(reviewerId)
            .operation("reject")
            .field("hasReason", reason != null && !reason.trim().isEmpty());
        
        opLogger.operationStarted("Content rejection signal", context);
        
        if (workflowState != null && workflowState.getStatus().canBeRejected()) {
            workflowState.reject(reviewerId, reason);
            // Update database status immediately
            persistenceActivity.updateContentStatus(workflowState.getContentId(), 
                ContentStatus.REJECTED.getDatabaseValue());
            rejectionReceived = true;
            
            opLogger.auditEvent("Content rejected by reviewer", 
                context.status("REJECTED").errorReason(reason != null ? reason : "No reason provided"));
            opLogger.operationCompleted("Content rejection signal", context);
        } else {
            String currentState = workflowState != null ? workflowState.getStatus().toString() : "null";
            opLogger.businessRuleViolation("Rejection state transition", 
                context.status(currentState), "Content not in rejectable state");
        }
    }
    
    @Override
    public void requestChanges(String reviewerId, String changeRequests) {
        LogContext context = LogContext.withContentAndWorkflow(
                workflowState != null ? workflowState.getContentId() : null, 
                Workflow.getInfo().getWorkflowId())
            .userId(reviewerId)
            .operation("requestChanges")
            .field("hasChangeRequests", changeRequests != null && !changeRequests.trim().isEmpty());
        
        opLogger.operationStarted("Change request signal", context);
        
        if (workflowState != null && workflowState.getStatus().canRequestChanges()) {
            workflowState.requestChanges(reviewerId, changeRequests);
            // Update database status immediately
            persistenceActivity.updateContentStatus(workflowState.getContentId(), 
                ContentStatus.CHANGES_REQUESTED.getDatabaseValue());
            changesRequested = true;
            
            opLogger.auditEvent("Changes requested by reviewer", 
                context.status("CHANGES_REQUESTED").field("changeDetails", "present"));
            opLogger.operationCompleted("Change request signal", context);
        } else {
            String currentState = workflowState != null ? workflowState.getStatus().toString() : "null";
            opLogger.businessRuleViolation("Change request state transition", 
                context.status(currentState), "Content not in changeable state");
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