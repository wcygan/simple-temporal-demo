package com.wcygan.contentapproval.exception;

/**
 * Exception thrown when attempting to access content that does not exist.
 */
public class ContentNotFoundException extends ContentServiceException {
    
    private static final String ERROR_CODE = "CONTENT_NOT_FOUND";
    
    public ContentNotFoundException(Long contentId) {
        super("Content not found with ID: " + contentId, ERROR_CODE);
    }
    
    public ContentNotFoundException(Long contentId, Throwable cause) {
        super("Content not found with ID: " + contentId, ERROR_CODE, cause);
    }
    
    @Override
    public boolean isRetryable() {
        return false; // Missing content won't appear on retry
    }
}