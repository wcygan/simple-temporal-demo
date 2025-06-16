package com.wcygan.contentapproval.exception;

/**
 * Exception thrown when notification delivery fails.
 */
public class NotificationException extends ContentServiceException {
    
    private static final String ERROR_CODE = "NOTIFICATION_ERROR";
    
    public NotificationException(String message) {
        super(message, ERROR_CODE);
    }
    
    public NotificationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
    
    @Override
    public boolean isRetryable() {
        return true; // Notification delivery might succeed on retry
    }
    
    @Override
    public boolean isUserFacing() {
        return false; // Notification system errors should not be exposed
    }
}