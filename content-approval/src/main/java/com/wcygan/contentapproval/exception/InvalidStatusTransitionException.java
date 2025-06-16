package com.wcygan.contentapproval.exception;

import com.wcygan.contentapproval.workflow.ContentStatus;

/**
 * Exception thrown when attempting an invalid content status transition.
 */
public class InvalidStatusTransitionException extends ContentServiceException {
    
    private static final String ERROR_CODE = "INVALID_STATUS_TRANSITION";
    
    public InvalidStatusTransitionException(ContentStatus from, ContentStatus to) {
        super(String.format("Invalid status transition from %s to %s", from, to), ERROR_CODE);
    }
    
    public InvalidStatusTransitionException(String from, String to) {
        super(String.format("Invalid status transition from %s to %s", from, to), ERROR_CODE);
    }
    
    @Override
    public boolean isRetryable() {
        return false; // Invalid transitions are business logic errors
    }
}