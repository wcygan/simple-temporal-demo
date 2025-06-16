package com.wcygan.contentapproval.exception;

/**
 * Exception thrown when database operations related to content fail.
 */
public class ContentPersistenceException extends ContentServiceException {
    
    private static final String ERROR_CODE = "CONTENT_PERSISTENCE_ERROR";
    
    public ContentPersistenceException(String message) {
        super(message, ERROR_CODE);
    }
    
    public ContentPersistenceException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
    
    @Override
    public boolean isRetryable() {
        return true; // Database operations might succeed on retry
    }
    
    @Override
    public boolean isUserFacing() {
        return false; // Database errors should not expose internal details
    }
}