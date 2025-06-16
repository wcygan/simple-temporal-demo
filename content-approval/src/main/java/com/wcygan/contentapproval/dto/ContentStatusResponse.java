package com.wcygan.contentapproval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wcygan.contentapproval.workflow.ContentApprovalState;

import java.time.LocalDateTime;

/**
 * DTO for content status responses.
 */
public class ContentStatusResponse {
    
    @JsonProperty("contentId")
    private Long contentId;
    
    @JsonProperty("workflowId")
    private String workflowId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("authorId")
    private String authorId;
    
    @JsonProperty("currentReviewerId")
    private String currentReviewerId;
    
    @JsonProperty("isComplete")
    private boolean isComplete;
    
    @JsonProperty("submittedAt")
    private LocalDateTime submittedAt;
    
    @JsonProperty("reviewStartedAt")
    private LocalDateTime reviewStartedAt;
    
    @JsonProperty("completedAt")
    private LocalDateTime completedAt;
    
    @JsonProperty("revisionCount")
    private int revisionCount;
    
    @JsonProperty("approvalComments")
    private String approvalComments;
    
    @JsonProperty("rejectionReason")
    private String rejectionReason;
    
    @JsonProperty("changeRequests")
    private String changeRequests;
    
    // Default constructor
    public ContentStatusResponse() {}
    
    // Constructor from workflow state
    public ContentStatusResponse(String workflowId, ContentApprovalState state) {
        this.workflowId = workflowId;
        if (state != null) {
            this.contentId = state.getContentId();
            this.status = state.getStatus() != null ? state.getStatus().toString() : "UNKNOWN";
            this.authorId = state.getAuthorId();
            this.currentReviewerId = state.getCurrentReviewerId();
            this.isComplete = state.isComplete();
            this.submittedAt = state.getSubmittedAt();
            this.reviewStartedAt = state.getReviewStartedAt();
            this.completedAt = state.getCompletedAt();
            this.revisionCount = state.getRevisionCount();
            this.approvalComments = state.getApprovalComments();
            this.rejectionReason = state.getRejectionReason();
            this.changeRequests = state.getChangeRequests();
        }
    }
    
    // Static factory method for not found
    public static ContentStatusResponse notFound(Long contentId) {
        ContentStatusResponse response = new ContentStatusResponse();
        response.contentId = contentId;
        response.status = "NOT_FOUND";
        response.isComplete = false;
        return response;
    }
    
    // Getters and setters
    public Long getContentId() {
        return contentId;
    }
    
    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
    
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAuthorId() {
        return authorId;
    }
    
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }
    
    public String getCurrentReviewerId() {
        return currentReviewerId;
    }
    
    public void setCurrentReviewerId(String currentReviewerId) {
        this.currentReviewerId = currentReviewerId;
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public void setComplete(boolean complete) {
        isComplete = complete;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public LocalDateTime getReviewStartedAt() {
        return reviewStartedAt;
    }
    
    public void setReviewStartedAt(LocalDateTime reviewStartedAt) {
        this.reviewStartedAt = reviewStartedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public int getRevisionCount() {
        return revisionCount;
    }
    
    public void setRevisionCount(int revisionCount) {
        this.revisionCount = revisionCount;
    }
    
    public String getApprovalComments() {
        return approvalComments;
    }
    
    public void setApprovalComments(String approvalComments) {
        this.approvalComments = approvalComments;
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }
    
    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
    
    public String getChangeRequests() {
        return changeRequests;
    }
    
    public void setChangeRequests(String changeRequests) {
        this.changeRequests = changeRequests;
    }
    
    @Override
    public String toString() {
        return String.format("ContentStatusResponse{contentId=%d, status='%s', complete=%b}", 
            contentId, status, isComplete);
    }
}