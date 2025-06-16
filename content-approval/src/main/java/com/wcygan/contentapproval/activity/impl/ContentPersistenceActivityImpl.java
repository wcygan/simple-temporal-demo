package com.wcygan.contentapproval.activity.impl;

import com.wcygan.contentapproval.activity.ContentPersistenceActivity;
import com.wcygan.contentapproval.exception.ContentNotFoundException;
import com.wcygan.contentapproval.exception.ContentPersistenceException;
import com.wcygan.contentapproval.generated.tables.records.ContentRecord;
import io.temporal.activity.Activity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static com.wcygan.contentapproval.generated.Tables.CONTENT;

/**
 * Implementation of ContentPersistenceActivity.
 * Handles all database operations related to content management using jOOQ.
 */
@ApplicationScoped
public class ContentPersistenceActivityImpl implements ContentPersistenceActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ContentPersistenceActivityImpl.class);
    
    @Inject
    DSLContext dsl;
    
    @Override
    @Transactional
    public void linkContentToWorkflow(Long contentId, String workflowId) {
        logger.info("Linking content {} to workflow {}", contentId, workflowId);
        
        try {
            Activity.getExecutionContext().heartbeat("Linking content to workflow");
            
            int updated = dsl.update(CONTENT)
                    .set(CONTENT.TEMPORAL_WORKFLOW_ID, workflowId)
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            if (updated == 0) {
                throw new ContentNotFoundException(contentId);
            }
            
            logger.info("Successfully linked content {} to workflow {}", contentId, workflowId);
            
        } catch (ContentNotFoundException e) {
            logger.warn("Content not found when linking to workflow: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error linking content {} to workflow {}", contentId, workflowId, e);
            throw new ContentPersistenceException("Failed to link content to workflow due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error linking content {} to workflow {}", contentId, workflowId, e);
            throw new ContentPersistenceException("Failed to link content to workflow", e);
        }
    }
    
    @Override
    @Transactional
    public void updateContentStatus(Long contentId, String status) {
        logger.info("Updating content {} status to {}", contentId, status);
        
        try {
            Activity.getExecutionContext().heartbeat("Updating content status");
            
            int updated = dsl.update(CONTENT)
                    .set(CONTENT.STATUS, status)
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            if (updated == 0) {
                throw new ContentNotFoundException(contentId);
            }
            
            logger.info("Successfully updated content {} status to {}", contentId, status);
            
        } catch (ContentNotFoundException e) {
            logger.warn("Content not found when updating status: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error updating content {} status to {}", contentId, status, e);
            throw new ContentPersistenceException("Failed to update content status due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error updating content {} status to {}", contentId, status, e);
            throw new ContentPersistenceException("Failed to update content status", e);
        }
    }
    
    @Override
    @Transactional
    public void publishContent(Long contentId) {
        logger.info("Publishing content {}", contentId);
        
        try {
            Activity.getExecutionContext().heartbeat("Publishing content");
            
            int updated = dsl.update(CONTENT)
                    .set(CONTENT.STATUS, "PUBLISHED")
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            if (updated == 0) {
                throw new ContentNotFoundException(contentId);
            }
            
            logger.info("Successfully published content {}", contentId);
            
        } catch (ContentNotFoundException e) {
            logger.warn("Content not found when publishing: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            logger.error("Database error publishing content {}", contentId, e);
            throw new ContentPersistenceException("Failed to publish content due to database error", e);
        } catch (Exception e) {
            logger.error("Unexpected error publishing content {}", contentId, e);
            throw new ContentPersistenceException("Failed to publish content", e);
        }
    }
    
    @Override
    @Transactional
    public void archiveContent(Long contentId) {
        logger.info("Archiving content {}", contentId);
        
        try {
            Activity.getExecutionContext().heartbeat("Archiving content");
            
            int updated = dsl.update(CONTENT)
                    .set(CONTENT.STATUS, "ARCHIVED")
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            if (updated == 0) {
                throw new RuntimeException("Content not found with ID: " + contentId);
            }
            
            logger.info("Successfully archived content {}", contentId);
            
        } catch (Exception e) {
            logger.error("Error archiving content {}", contentId, e);
            throw new RuntimeException("Failed to archive content", e);
        }
    }
    
    @Override
    public String getContentDetails(Long contentId) {
        logger.info("Retrieving content details for {}", contentId);
        
        try {
            Activity.getExecutionContext().heartbeat("Retrieving content details");
            
            ContentRecord content = dsl.selectFrom(CONTENT)
                    .where(CONTENT.ID.eq(contentId))
                    .fetchOne();
            
            if (content == null) {
                throw new RuntimeException("Content not found with ID: " + contentId);
            }
            
            // Return content details as JSON-like string
            String details = String.format(
                "{\"id\":%d,\"title\":\"%s\",\"authorId\":\"%s\",\"status\":\"%s\",\"workflowId\":\"%s\"," +
                "\"createdDate\":\"%s\",\"updatedDate\":\"%s\"}",
                content.getId(),
                content.getTitle() != null ? content.getTitle().replace("\"", "\\\"") : "",
                content.getAuthorId() != null ? content.getAuthorId() : "",
                content.getStatus() != null ? content.getStatus() : "",
                content.getTemporalWorkflowId() != null ? content.getTemporalWorkflowId() : "",
                content.getCreatedDate() != null ? content.getCreatedDate().toString() : "",
                content.getUpdatedDate() != null ? content.getUpdatedDate().toString() : ""
            );
            
            logger.info("Retrieved content details for {}", contentId);
            return details;
            
        } catch (Exception e) {
            logger.error("Error retrieving content details for {}", contentId, e);
            throw new RuntimeException("Failed to retrieve content details", e);
        }
    }
    
    @Override
    @Transactional
    public void updateReviewMetadata(Long contentId, String reviewerId, String reviewComments) {
        logger.info("Updating review metadata for content {} by reviewer {}", contentId, reviewerId);
        
        try {
            Activity.getExecutionContext().heartbeat("Updating review metadata");
            
            // In this simple implementation, we'll just update the status and timestamp
            // In a more complex system, we might have separate reviewer fields or a review history table
            int updated = dsl.update(CONTENT)
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            if (updated == 0) {
                throw new RuntimeException("Content not found with ID: " + contentId);
            }
            
            // Log the review action (in production, this would go to an audit table)
            logger.info("Review metadata updated for content {} by reviewer {}: {}", 
                contentId, reviewerId, reviewComments);
            
        } catch (Exception e) {
            logger.error("Error updating review metadata for content {} by reviewer {}", 
                contentId, reviewerId, e);
            throw new RuntimeException("Failed to update review metadata", e);
        }
    }
    
    @Override
    @Transactional
    public void createAuditLog(Long contentId, String action, String userId, String details) {
        logger.info("Creating audit log for content {}: {} by user {}", contentId, action, userId);
        
        try {
            Activity.getExecutionContext().heartbeat("Creating audit log");
            
            // In this simple implementation, we'll just log to the application log
            // In a production system, this would insert into an audit_log table
            logger.info("AUDIT: Content {} - Action: {} - User: {} - Details: {}", 
                contentId, action, userId, details);
            
            // Update the content's updated timestamp to reflect the audit action
            dsl.update(CONTENT)
                    .set(CONTENT.UPDATED_DATE, LocalDateTime.now())
                    .where(CONTENT.ID.eq(contentId))
                    .execute();
            
            logger.info("Audit log created for content {}", contentId);
            
        } catch (Exception e) {
            logger.error("Error creating audit log for content {}", contentId, e);
            throw new RuntimeException("Failed to create audit log", e);
        }
    }
}