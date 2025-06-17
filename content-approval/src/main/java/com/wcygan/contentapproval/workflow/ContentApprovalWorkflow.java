package com.wcygan.contentapproval.workflow;

import com.wcygan.contentapproval.dto.WorkflowConfiguration;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.time.Duration;

@WorkflowInterface
public interface ContentApprovalWorkflow {
    
    /**
     * Main workflow method that orchestrates the content approval process.
     * 
     * @param contentId The ID of the content to process
     * @param authorId The ID of the content author
     * @param configuration Workflow configuration settings
     * @return The final workflow execution ID
     */
    @WorkflowMethod(name = "ContentApproval")
    String processContentApproval(Long contentId, String authorId, WorkflowConfiguration configuration);
    
    /**
     * Helper method for tests.
     * Creates default configuration settings for backward compatibility.
     * 
     * @return Default WorkflowConfiguration
     */
    static WorkflowConfiguration createDefaultConfiguration() {
        return new WorkflowConfiguration(
            java.time.Duration.ofDays(7), // 7 day review timeout
            true,  // validation enabled
            true,  // auto-publish enabled
            true   // notification enabled
        );
    }
    
    /**
     * Backward compatibility method for tests.
     * Uses default configuration settings.
     * 
     * @param workflow The workflow instance to call
     * @param contentId The ID of the content to process
     * @param authorId The ID of the content author
     * @return The final workflow execution ID
     */
    static String processWithDefaults(ContentApprovalWorkflow workflow, Long contentId, String authorId) {
        return workflow.processContentApproval(contentId, authorId, createDefaultConfiguration());
    }
    
    /**
     * Signal method to approve content.
     * 
     * @param approverId The ID of the user approving the content
     * @param comments Optional approval comments
     */
    @SignalMethod
    void approve(String approverId, String comments);
    
    /**
     * Signal method to reject content.
     * 
     * @param reviewerId The ID of the user rejecting the content
     * @param reason The reason for rejection
     */
    @SignalMethod
    void reject(String reviewerId, String reason);
    
    /**
     * Signal method to request changes to content.
     * 
     * @param reviewerId The ID of the user requesting changes
     * @param changeRequests The requested changes
     */
    @SignalMethod
    void requestChanges(String reviewerId, String changeRequests);
    
    /**
     * Query method to get the current workflow state.
     * 
     * @return Current workflow state
     */
    @QueryMethod
    ContentApprovalState getWorkflowState();
    
    /**
     * Query method to get the current approval status.
     * 
     * @return Current approval status
     */
    @QueryMethod
    String getApprovalStatus();
    
    /**
     * Query method to check if the workflow is complete.
     * 
     * @return True if workflow is complete, false otherwise
     */
    @QueryMethod
    boolean isComplete();
}