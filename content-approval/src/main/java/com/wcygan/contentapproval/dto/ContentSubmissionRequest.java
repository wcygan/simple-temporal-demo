package com.wcygan.contentapproval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO for content submission requests.
 */
public class ContentSubmissionRequest {
    
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    @JsonProperty("title")
    private String title;
    
    @NotBlank(message = "Content is required")
    @Size(min = 10, max = 50000, message = "Content must be between 10 and 50000 characters")
    @JsonProperty("content")
    private String content;
    
    @NotBlank(message = "Author ID is required")
    @JsonProperty("authorId")
    private String authorId;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    // Default constructor
    public ContentSubmissionRequest() {}
    
    // Constructor with required fields
    public ContentSubmissionRequest(String title, String content, String authorId) {
        this.title = title;
        this.content = content;
        this.authorId = authorId;
    }
    
    // Getters and setters
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
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    @Override
    public String toString() {
        return String.format("ContentSubmissionRequest{title='%s', authorId='%s', tagsCount=%d}", 
            title, authorId, tags != null ? tags.size() : 0);
    }
}