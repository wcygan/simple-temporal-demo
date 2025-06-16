package com.wcygan.contentapproval.notification;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a notification request with recipient, content, and metadata.
 */
public class NotificationRequest {
    
    private final String recipient;
    private final String subject;
    private final String message;
    private final NotificationType type;
    private final Map<String, Object> metadata;
    
    public NotificationRequest(String recipient, String subject, String message, NotificationType type) {
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.type = type;
        this.metadata = new HashMap<>();
    }
    
    public NotificationRequest(String recipient, String subject, String message, 
                              NotificationType type, Map<String, Object> metadata) {
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.type = type;
        this.metadata = new HashMap<>(metadata);
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public String getMessage() {
        return message;
    }
    
    public NotificationType getType() {
        return type;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public NotificationRequest withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new NotificationRequest(recipient, subject, message, type, newMetadata);
    }
    
    @Override
    public String toString() {
        return "NotificationRequest{" +
                "recipient='" + recipient + '\'' +
                ", subject='" + subject + '\'' +
                ", type=" + type +
                ", metadata=" + metadata +
                '}';
    }
}