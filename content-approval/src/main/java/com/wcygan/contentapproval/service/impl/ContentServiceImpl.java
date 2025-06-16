package com.wcygan.contentapproval.service.impl;

import com.wcygan.contentapproval.config.TemporalWorkerConfig;
import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import com.wcygan.contentapproval.service.ContentService;
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

import static com.wcygan.contentapproval.generated.Tables.CONTENT;

/**
 * Implementation of ContentService.
 * Handles business logic for content management and Temporal workflow integration.
 */
@ApplicationScoped
public class ContentServiceImpl implements ContentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentServiceImpl.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @Inject
    DSLContext dsl;
    
    @Override
    @Transactional
    public ContentApprovalResponse submitContentForApproval(ContentSubmissionRequest request) {
        logger.info("Submitting content for approval: {}", request);
        
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
            
            logger.info("Created content record with ID: {}", contentId);
            
            // Generate workflow ID
            String workflowId = String.format("content-approval-%d-%d", contentId, System.currentTimeMillis());
            
            // Configure workflow options
            WorkflowOptions workflowOptions = WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue(TemporalWorkerConfig.CONTENT_APPROVAL_TASK_QUEUE)
                    .setWorkflowExecutionTimeout(Duration.ofDays(30)) // 30 days max for approval process
                    .build();
            
            // Create workflow stub and start execution
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowOptions);
            
            // Start workflow asynchronously
            WorkflowClient.start(workflow::processContentApproval, contentId, request.getAuthorId());
            
            logger.info("Started content approval workflow: {} for content: {}", workflowId, contentId);
            
            return ContentApprovalResponse.success(contentId, workflowId);
            
        } catch (Exception e) {
            logger.error("Error submitting content for approval: {}", request, e);
            return ContentApprovalResponse.error(null, "Failed to submit content for approval: " + e.getMessage());
        }
    }
    
    @Override
    public ContentStatusResponse getContentStatus(Long contentId) {
        logger.info("Getting content status for ID: {}", contentId);
        
        try {
            // Get content record from database
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                logger.warn("Content not found with ID: {}", contentId);
                return ContentStatusResponse.notFound(contentId);
            }
            
            String workflowId = content.getTemporalWorkflowId();
            if (workflowId == null) {
                // Content exists but no workflow started
                ContentStatusResponse response = new ContentStatusResponse();
                response.setContentId(contentId);
                response.setStatus(content.getStatus());
                response.setComplete(false);
                return response;
            }
            
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
            logger.error("Error getting content status for ID: {}", contentId, e);
            return ContentStatusResponse.notFound(contentId);
        }
    }
    
    @Override
    public boolean approveContent(Long contentId, String approverId, String comments) {
        logger.info("Approving content {} by reviewer {}", contentId, approverId);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                logger.error("No workflow found for content ID: {}", contentId);
                return false;
            }
            
            // Send approval signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.approve(approverId, comments);
            
            logger.info("Approval signal sent for content {} by reviewer {}", contentId, approverId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error approving content {} by reviewer {}", contentId, approverId, e);
            return false;
        }
    }
    
    @Override
    public boolean rejectContent(Long contentId, String reviewerId, String reason) {
        logger.info("Rejecting content {} by reviewer {}", contentId, reviewerId);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                logger.error("No workflow found for content ID: {}", contentId);
                return false;
            }
            
            // Send rejection signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.reject(reviewerId, reason);
            
            logger.info("Rejection signal sent for content {} by reviewer {}", contentId, reviewerId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error rejecting content {} by reviewer {}", contentId, reviewerId, e);
            return false;
        }
    }
    
    @Override
    public boolean requestChanges(Long contentId, String reviewerId, String changeRequests) {
        logger.info("Requesting changes for content {} by reviewer {}", contentId, reviewerId);
        
        try {
            String workflowId = getWorkflowIdForContent(contentId);
            if (workflowId == null) {
                logger.error("No workflow found for content ID: {}", contentId);
                return false;
            }
            
            // Send change request signal to workflow
            ContentApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                    ContentApprovalWorkflow.class, workflowId);
            
            workflow.requestChanges(reviewerId, changeRequests);
            
            logger.info("Change request signal sent for content {} by reviewer {}", contentId, reviewerId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error requesting changes for content {} by reviewer {}", contentId, reviewerId, e);
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