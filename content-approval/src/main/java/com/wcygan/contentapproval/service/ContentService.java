package com.wcygan.contentapproval.service;

import com.wcygan.contentapproval.dto.ContentApprovalResponse;
import com.wcygan.contentapproval.dto.ContentStatusResponse;
import com.wcygan.contentapproval.dto.ContentSubmissionRequest;

/**
 * Service interface for content management operations.
 */
public interface ContentService {
    
    /**
     * Submits content for approval workflow.
     * 
     * @param request The content submission request
     * @return The approval response with workflow information
     */
    ContentApprovalResponse submitContentForApproval(ContentSubmissionRequest request);
    
    /**
     * Gets the current status of content approval workflow.
     * 
     * @param contentId The content ID
     * @return The current status response
     */
    ContentStatusResponse getContentStatus(Long contentId);
    
    /**
     * Approves content by sending a signal to the workflow.
     * 
     * @param contentId The content ID
     * @param approverId The ID of the approver
     * @param comments Optional approval comments
     * @return True if signal was sent successfully
     */
    boolean approveContent(Long contentId, String approverId, String comments);
    
    /**
     * Rejects content by sending a signal to the workflow.
     * 
     * @param contentId The content ID
     * @param reviewerId The ID of the reviewer
     * @param reason The rejection reason
     * @return True if signal was sent successfully
     */
    boolean rejectContent(Long contentId, String reviewerId, String reason);
    
    /**
     * Requests changes for content by sending a signal to the workflow.
     * 
     * @param contentId The content ID
     * @param reviewerId The ID of the reviewer
     * @param changeRequests The requested changes
     * @return True if signal was sent successfully
     */
    boolean requestChanges(Long contentId, String reviewerId, String changeRequests);
}