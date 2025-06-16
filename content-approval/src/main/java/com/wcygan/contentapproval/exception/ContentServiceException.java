package com.wcygan.contentapproval.exception;

/**
 * Base exception for all content service related errors.
 * Provides a foundation for more specific exception types.
 */
public abstract class ContentServiceException extends RuntimeException {
    
    private final String errorCode;
    
    protected ContentServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    protected ContentServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Returns whether this exception represents a recoverable error
     * that could succeed on retry.
     */
    public boolean isRetryable() {
        return false; // Default: not retryable
    }
    
    /**
     * Returns whether this exception should be exposed to end users
     * or should be logged and replaced with a generic error message.
     */
    public boolean isUserFacing() {
        return true; // Default: safe to show to users
    }
}