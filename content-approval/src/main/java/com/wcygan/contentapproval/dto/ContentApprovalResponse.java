package com.wcygan.contentapproval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * DTO for content approval responses.
 */
public class ContentApprovalResponse {
    
    @JsonProperty("contentId")
    private Long contentId;
    
    @JsonProperty("workflowId")
    private String workflowId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("submittedAt")
    private LocalDateTime submittedAt;
    
    // Default constructor
    public ContentApprovalResponse() {}
    
    // Constructor for successful submission
    public ContentApprovalResponse(Long contentId, String workflowId, String status, String message) {
        this.contentId = contentId;
        this.workflowId = workflowId;
        this.status = status;
        this.message = message;
        this.submittedAt = LocalDateTime.now();
    }
    
    // Static factory methods
    public static ContentApprovalResponse success(Long contentId, String workflowId) {
        return new ContentApprovalResponse(contentId, workflowId, "SUBMITTED", 
            "Content submitted successfully for approval");
    }
    
    public static ContentApprovalResponse error(Long contentId, String message) {
        return new ContentApprovalResponse(contentId, null, "ERROR", message);
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
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    @Override
    public String toString() {
        return String.format("ContentApprovalResponse{contentId=%d, workflowId='%s', status='%s'}", 
            contentId, workflowId, status);
    }
}