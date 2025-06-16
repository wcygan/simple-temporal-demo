package com.wcygan.contentapproval.notification;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of a notification attempt.
 */
public class NotificationResult {
    
    private final boolean success;
    private final String messageId;
    private final String errorMessage;
    private final LocalDateTime sentAt;
    private final Map<String, Object> metadata;
    
    private NotificationResult(boolean success, String messageId, String errorMessage, 
                              LocalDateTime sentAt, Map<String, Object> metadata) {
        this.success = success;
        this.messageId = messageId;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
        this.metadata = new HashMap<>(metadata);
    }
    
    /**
     * Creates a successful notification result.
     */
    public static NotificationResult success(String messageId) {
        return new NotificationResult(true, messageId, null, LocalDateTime.now(), Map.of());
    }
    
    /**
     * Creates a successful notification result with metadata.
     */
    public static NotificationResult success(String messageId, Map<String, Object> metadata) {
        return new NotificationResult(true, messageId, null, LocalDateTime.now(), metadata);
    }
    
    /**
     * Creates a failed notification result.
     */
    public static NotificationResult failure(String errorMessage) {
        return new NotificationResult(false, null, errorMessage, LocalDateTime.now(), Map.of());
    }
    
    /**
     * Creates a failed notification result with metadata.
     */
    public static NotificationResult failure(String errorMessage, Map<String, Object> metadata) {
        return new NotificationResult(false, null, errorMessage, LocalDateTime.now(), metadata);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public LocalDateTime getSentAt() {
        return sentAt;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        return "NotificationResult{" +
                "success=" + success +
                ", messageId='" + messageId + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", sentAt=" + sentAt +
                '}';
    }
}