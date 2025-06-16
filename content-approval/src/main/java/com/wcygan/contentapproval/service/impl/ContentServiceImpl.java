package com.wcygan.contentapproval.service.impl;

import com.wcygan.contentapproval.config.TemporalWorkerConfig;
import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;
import com.wcygan.contentapproval.dto.WorkflowConfiguration;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import com.wcygan.contentapproval.logging.LogContext;
import com.wcygan.contentapproval.logging.OperationLogger;
import com.wcygan.contentapproval.service.ContentService;
import com.wcygan.contentapproval.service.WorkflowConfigurationService;
import com.wcygan.contentapproval.workflow.ContentApprovalState;
import com.wcygan.contentapproval.workflow.ContentApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;

/**
 * Implementation of ContentService.
 * Handles business logic for content management and Temporal workflow integration.
 */
@ApplicationScoped
public class ContentServiceImpl implements ContentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentServiceImpl.class);
    private final OperationLogger opLogger = new OperationLogger(logger);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    DSLContext dsl;
    
    @Inject
    WorkflowConfigurationService configurationService;
    
    @Override
    @Transactional
    public ContentApprovalResponse submitContentForApproval(ContentSubmissionRequest request) {
        LogContext context = LogContext.create()
            .userId(request.getAuthorId())
            .operation("submitContentForApproval")
            .field("titleLength", request.getTitle() != null ? request.getTitle().length() : 0)
            .field("contentLength", request.getContent() != null ? request.getContent().length() : 0);
        
        opLogger.operationStarted("Content submission", context);
        
        // Debug expensive request object logging
        opLogger.debugExpensive("Content submission request details", context, request);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Create content record in database
            ContentRecord content = dsl.newRecord(CONTENT);
            content.setTitle(request.getTitle());
            content.setContent(request.getContent());
            content.setAuthorId(request.getAuthorId());
            content.setStatus("DRAFT");
            content.setCreatedDate(LocalDateTime.now());
            content.setUpdatedDate(LocalDateTime.now());
            
            // Set tags if provided (simplified as null for now due to JSON complexity)
            content.setTags(null);
            
            content.store();
            Long contentId = content.getId();
            
            // Update context with content ID
            context.contentId(contentId);
            opLogger.operationCompleted("Content record creation", context);
            
            // Generate workflow ID
            String workflowId = String.format("content-approval-%d-%d", contentId, System.currentTimeMillis());
            context.workflowId(workflowId);
            
            // Create workflow configuration
            WorkflowConfiguration configuration = new WorkflowConfiguration(
                    configurationService.getReviewTimeout(),
                    configurationService.isValidationEnabled(),
                    configurationService.isAutoPublishEnabled(),
                    configurationService.isNotificationEnabled()
            );
            
            // Configure workflow options
            WorkflowOptions workflowOptions = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(configurationService.getTaskQueue())
                    .setWorkflowExecutionTimeout(configurationService.getWorkflowExecutionTimeout())
                    .build();
            
            // Create workflow stub and start execution
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowOptions);
            
            // Start workflow asynchronously using the 3-parameter method explicitly
            CompletableFuture<String> workflowFuture = WorkflowClient.execute(
                () -> workflow.processContentApproval(contentId, request.getAuthorId(), configuration));
            
            long duration = System.currentTimeMillis() - startTime;
            opLogger.operationCompleted("Content approval workflow start", context, duration);
            
            // Audit event for content submission
            opLogger.auditEvent("Content submitted for approval", context);
            
            return ContentApprovalResponse.success(contentId, workflowId);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            opLogger.operationFailed("Content submission", context.duration(duration), 
                "System error during submission", e);
            return ContentApprovalResponse.error(null, "Failed to submit content for approval: " + e.getMessage());
        }
    }
    
    @Override
    public ContentStatusResponse getContentStatus(Long contentId) {
        LogContext context = LogContext.withContentId(contentId).operation("getContentStatus");
        opLogger.operationStarted("Content status query", context);
        
        try {
            // Get content record from database
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                opLogger.businessRuleViolation("Content existence", context, "Content not found");
                return ContentStatusResponse.notFound(contentId);
            }
            
            context.status(content.getStatus());
            
            String workflowId = content.getTemporalWorkflowId();
            if (workflowId == null) {
                // Content exists but no workflow started
                opLogger.operationCompleted("Content status query (no workflow)", context);
                ContentStatusResponse response = new ContentStatusResponse();
                response.setContentId(contentId);
                response.setStatus(content.getStatus());
                response.setComplete(false);
                return response;
            }
            
            context.workflowId(workflowId);
            
            // Query workflow state
            try {
                ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                        ContentApprovalWorkflow.class, workflowId);
                
                ContentApprovalState workflowState = workflow.getWorkflowState();
                
                return new ContentStatusResponse(workflowId, workflowState);
                
            } catch (Exception e) {
                logger.warn("Could not query workflow state for ID: {}, falling back to database status", workflowId, e);
                
                // Fallback to database status if workflow query fails
                ContentStatusResponse response = new ContentStatusResponse();
                response.setContentId(contentId);
                response.setWorkflowId(workflowId);
                response.setStatus(content.getStatus());
                response.setComplete("PUBLISHED".equals(content.getStatus()) || "REJECTED".equals(content.getStatus()));
                return response;
            }
            
        } catch (Exception e) {
            opLogger.operationFailed("Content status query", context, 
                "Database error during status query", e);
            return ContentStatusResponse.notFound(contentId);
        }
    }
    
    @Override
    public boolean approveContent(Long contentId, String approverId, String comments) {
        LogContext context = LogContext.withContentId(contentId)
            .userId(approverId)
            .operation("approveContent")
            .field("hasComments", comments != null && !comments.trim().isEmpty());
        
        opLogger.operationStarted("Content approval", context);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                opLogger.businessRuleViolation("Workflow existence", context, "No workflow found for content");
                return false;
            }
            
            context.workflowId(workflowId);
            
            // Send approval signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.approve(approverId, comments);
            
            opLogger.auditEvent("Content approval signal sent", context);
            opLogger.operationCompleted("Content approval", context);
            return true;
            
        } catch (Exception e) {
            opLogger.operationFailed("Content approval", context, "System error during approval", e);
            return false;
        }
    }
    
    @Override
    public boolean rejectContent(Long contentId, String reviewerId, String reason) {
        LogContext context = LogContext.withContentId(contentId)
            .userId(reviewerId)
            .operation("rejectContent")
            .field("hasReason", reason != null && !reason.trim().isEmpty());
        
        opLogger.operationStarted("Content rejection", context);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                opLogger.businessRuleViolation("Workflow existence", context, "No workflow found for content");
                return false;
            }
            
            context.workflowId(workflowId);
            
            // Send rejection signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.reject(reviewerId, reason);
            
            opLogger.auditEvent("Content rejection signal sent", context.errorReason(reason));
            opLogger.operationCompleted("Content rejection", context);
            return true;
            
        } catch (Exception e) {
            opLogger.operationFailed("Content rejection", context, "System error during rejection", e);
            return false;
        }
    }
    
    @Override
    public boolean requestChanges(Long contentId, String reviewerId, String changeRequests) {
        LogContext context = LogContext.withContentId(contentId)
            .userId(reviewerId)
            .operation("requestChanges")
            .field("hasChangeRequests", changeRequests != null && !changeRequests.trim().isEmpty());
        
        opLogger.operationStarted("Change request", context);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                opLogger.businessRuleViolation("Workflow existence", context, "No workflow found for content");
                return false;
            }
            
            context.workflowId(workflowId);
            
            // Send change request signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.requestChanges(reviewerId, changeRequests);
            
            opLogger.auditEvent("Change request signal sent", context.field("requestDetails", "present"));
            opLogger.operationCompleted("Change request", context);
            return true;
            
        } catch (Exception e) {
            opLogger.operationFailed("Change request", context, "System error during change request", e);
            return false;
        }
    }
    
    /**
     * Helper method to get workflow ID for a content record.
     */
    private String getWorkflowIdForContent(Long contentId) {
        ContentRecord content = dsl.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .fetchOne();
        
        return content != null ? content.getTemporalWorkflowId() : null;
    }
}