package com.wcygan.contentapproval.workflow;

import java.time.LocalDateTime;

/**
 * Represents the state of a content approval workflow.
 * This class is used to track the current state and history of the approval process.
 */
public class ContentApprovalState {
    
    private Long contentId;
    private String authorId;
    private ContentStatus status;
    private String currentReviewerId;
    private String approvalComments;
    private String rejectionReason;
    private String changeRequests;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewStartedAt;
    private LocalDateTime completedAt;
    private int revisionCount;
    private boolean isComplete;
    
    public ContentApprovalState() {
        this.status = ContentStatus.DRAFT;
        this.revisionCount = 0;
        this.isComplete = false;
        this.submittedAt = LocalDateTime.now();
    }
    
    public ContentApprovalState(Long contentId, String authorId) {
        this();
        this.contentId = contentId;
        this.authorId = authorId;
    }
    
    // Getters and setters
    public Long getContentId() {
        return contentId;
    }
    
    public void setContentId(Long contentId) {
        this.contentId = contentId;
    }
    
    public String getAuthorId() {
        return authorId;
    }
    
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }
    
    public ContentStatus getStatus() {
        return status;
    }
    
    public void setStatus(ContentStatus status) {
        this.status = status;
    }
    
    public String getCurrentReviewerId() {
        return currentReviewerId;
    }
    
    public void setCurrentReviewerId(String currentReviewerId) {
        this.currentReviewerId = currentReviewerId;
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
    
    public void incrementRevisionCount() {
        this.revisionCount++;
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public void setComplete(boolean complete) {
        isComplete = complete;
    }
    
    /**
     * Transitions the state to under review.
     */
    public void startReview() {
        this.status = ContentStatus.UNDER_REVIEW;
        this.reviewStartedAt = LocalDateTime.now();
    }
    
    /**
     * Transitions the state to approved.
     */
    public void approve(String approverId, String comments) {
        this.status = ContentStatus.APPROVED;
        this.currentReviewerId = approverId;
        this.approvalComments = comments;
        this.completedAt = LocalDateTime.now();
        this.isComplete = true;
    }
    
    /**
     * Transitions the state to rejected.
     */
    public void reject(String reviewerId, String reason) {
        this.status = ContentStatus.REJECTED;
        this.currentReviewerId = reviewerId;
        this.rejectionReason = reason;
        this.completedAt = LocalDateTime.now();
        this.isComplete = true;
    }
    
    /**
     * Transitions the state to changes requested.
     */
    public void requestChanges(String reviewerId, String changes) {
        this.status = ContentStatus.CHANGES_REQUESTED;
        this.currentReviewerId = reviewerId;
        this.changeRequests = changes;
        this.incrementRevisionCount();
    }
    
    @Override
    public String toString() {
        return String.format("ContentApprovalState{contentId=%d, status=%s, reviewerId='%s', complete=%b}", 
                contentId, status, currentReviewerId, isComplete);
    }
}