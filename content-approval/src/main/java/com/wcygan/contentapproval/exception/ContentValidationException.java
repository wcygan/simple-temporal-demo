package com.wcygan.contentapproval.exception;

/**
 * Exception thrown when content fails validation rules.
 */
public class ContentValidationException extends ContentServiceException {
    
    private static final String ERROR_CODE = "CONTENT_VALIDATION_ERROR";
    
    public ContentValidationException(String message) {
        super(message, ERROR_CODE);
    }
    
    public ContentValidationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
    
    @Override
    public boolean isRetryable() {
        return false; // Validation failures won't resolve on retry unless content changes
    }
}