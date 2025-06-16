package com.wcygan.contentapproval.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides structured logging context for consistent log formatting.
 * Helps maintain contextual information across operations.
 */
public class LogContext {
    
    private final Map<String, Object> context = new HashMap<>();
    
    private LogContext() {}
    
    /**
     * Creates a new log context.
     */
    public static LogContext create() {
        return new LogContext();
    }
    
    /**
     * Creates a log context with content ID.
     */
    public static LogContext withContentId(Long contentId) {
        return create().contentId(contentId);
    }
    
    /**
     * Creates a log context with workflow ID.
     */
    public static LogContext withWorkflowId(String workflowId) {
        return create().workflowId(workflowId);
    }
    
    /**
     * Creates a log context with content and workflow IDs.
     */
    public static LogContext withContentAndWorkflow(Long contentId, String workflowId) {
        return create().contentId(contentId).workflowId(workflowId);
    }
    
    /**
     * Adds content ID to context.
     */
    public LogContext contentId(Long contentId) {
        context.put("contentId", contentId);
        return this;
    }
    
    /**
     * Adds workflow ID to context.
     */
    public LogContext workflowId(String workflowId) {
        context.put("workflowId", workflowId);
        return this;
    }
    
    /**
     * Adds user ID to context.
     */
    public LogContext userId(String userId) {
        context.put("userId", userId);
        return this;
    }
    
    /**
     * Adds operation name to context.
     */
    public LogContext operation(String operation) {
        context.put("operation", operation);
        return this;
    }
    
    /**
     * Adds status to context.
     */
    public LogContext status(String status) {
        context.put("status", status);
        return this;
    }
    
    /**
     * Adds duration to context.
     */
    public LogContext duration(long durationMs) {
        context.put("durationMs", durationMs);
        return this;
    }
    
    /**
     * Adds error reason to context.
     */
    public LogContext errorReason(String reason) {
        context.put("errorReason", reason);
        return this;
    }
    
    /**
     * Adds custom field to context.
     */
    public LogContext field(String key, Object value) {
        context.put(key, value);
        return this;
    }
    
    /**
     * Builds a structured log message with context.
     */
    public String build(String message) {
        if (context.isEmpty()) {
            return message;
        }
        
        StringBuilder sb = new StringBuilder(message);
        sb.append(" [");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Returns context as a map for structured logging frameworks.
     */
    public Map<String, Object> asMap() {
        return new HashMap<>(context);
    }
    
    /**
     * Gets a specific context value.
     */
    public Object get(String key) {
        return context.get(key);
    }
    
    /**
     * Checks if context contains a key.
     */
    public boolean has(String key) {
        return context.containsKey(key);
    }
    
    @Override
    public String toString() {
        return build("");
    }
}