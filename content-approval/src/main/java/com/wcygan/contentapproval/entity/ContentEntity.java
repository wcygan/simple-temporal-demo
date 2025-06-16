package com.wcygan.contentapproval.entity;

import java.time.LocalDateTime;

/**
 * Domain entity representing content in the approval system.
 * This entity abstracts away database-specific details and provides a clean domain model.
 */
public class ContentEntity {
    
    private Long id;
    private String title;
    private String content;
    private String authorId;
    private String status;
    private String temporalWorkflowId;
    private String tags;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    
    // Default constructor
    public ContentEntity() {}
    
    // Constructor for new content
    public ContentEntity(String title, String content, String authorId) {
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.status = "DRAFT";
        this.createdDate = LocalDateTime.now();
        this.updatedDate = LocalDateTime.now();
    }
    
    // Full constructor
    public ContentEntity(Long id, String title, String content, String authorId, 
                        String status, String temporalWorkflowId, String tags,
                        LocalDateTime createdDate, LocalDateTime updatedDate) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
        this.status = status;
        this.temporalWorkflowId = temporalWorkflowId;
        this.tags = tags;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getAuthorId() {
        return authorId;
    }
    
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedDate = LocalDateTime.now();
    }
    
    public String getTemporalWorkflowId() {
        return temporalWorkflowId;
    }
    
    public void setTemporalWorkflowId(String temporalWorkflowId) {
        this.temporalWorkflowId = temporalWorkflowId;
        this.updatedDate = LocalDateTime.now();
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
    
    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }
    
    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
    
    // Business methods
    public boolean isDraft() {
        return "DRAFT".equals(status);
    }
    
    public boolean isUnderReview() {
        return "UNDER_REVIEW".equals(status);
    }
    
    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
    
    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }
    
    public boolean isRejected() {
        return "REJECTED".equals(status);
    }
    
    public boolean hasWorkflow() {
        return temporalWorkflowId != null && !temporalWorkflowId.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "ContentEntity{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", authorId='" + authorId + '\'' +
                ", status='" + status + '\'' +
                ", temporalWorkflowId='" + temporalWorkflowId + '\'' +
                ", createdDate=" + createdDate +
                ", updatedDate=" + updatedDate +
                '}';
    }
}