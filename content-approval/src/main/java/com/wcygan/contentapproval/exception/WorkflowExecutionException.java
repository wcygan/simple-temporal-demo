package com.wcygan.contentapproval.exception;

/**
 * Exception thrown when Temporal workflow operations fail.
 */
public class WorkflowExecutionException extends ContentServiceException {
    
    private static final String ERROR_CODE = "WORKFLOW_EXECUTION_ERROR";
    
    public WorkflowExecutionException(String message) {
        super(message, ERROR_CODE);
    }
    
    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
    
    @Override
    public boolean isRetryable() {
        return true; // Workflow operations might succeed on retry
    }
    
    @Override
    public boolean isUserFacing() {
        return false; // Workflow internals should not be exposed to users
    }
}