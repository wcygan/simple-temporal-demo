package com.wcygan.contentapproval.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for content persistence operations.
 * Handles all database operations related to content management.
 */
@ActivityInterface
public interface ContentPersistenceActivity {
    
    /**
     * Links content to a Temporal workflow by updating the temporal_workflow_id column.
     * 
     * @param contentId The ID of the content
     * @param workflowId The Temporal workflow ID
     */
    @ActivityMethod
    void linkContentToWorkflow(Long contentId, String workflowId);
    
    /**
     * Updates the status of content in the database.
     * 
     * @param contentId The ID of the content
     * @param status The new status to set
     */
    @ActivityMethod
    void updateContentStatus(Long contentId, String status);
    
    /**
     * Publishes content by updating its status to PUBLISHED.
     * 
     * @param contentId The ID of the content to publish
     */
    @ActivityMethod
    void publishContent(Long contentId);
    
    /**
     * Archives content by updating its status and adding archive timestamp.
     * 
     * @param contentId The ID of the content to archive
     */
    @ActivityMethod
    void archiveContent(Long contentId);
    
    /**
     * Retrieves content details for processing.
     * 
     * @param contentId The ID of the content
     * @return Content details as a JSON string
     */
    @ActivityMethod
    String getContentDetails(Long contentId);
    
    /**
     * Updates content metadata like reviewer information.
     * 
     * @param contentId The ID of the content
     * @param reviewerId The ID of the reviewer
     * @param reviewComments Comments from the reviewer
     */
    @ActivityMethod
    void updateReviewMetadata(Long contentId, String reviewerId, String reviewComments);
    
    /**
     * Creates an audit log entry for content state changes.
     * 
     * @param contentId The ID of the content
     * @param action The action performed
     * @param userId The user who performed the action
     * @param details Additional details about the action
     */
    @ActivityMethod
    void createAuditLog(Long contentId, String action, String userId, String details);
}