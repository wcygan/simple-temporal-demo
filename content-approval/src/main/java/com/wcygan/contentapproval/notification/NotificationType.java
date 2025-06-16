package com.wcygan.contentapproval.notification;

/**
 * Enumeration of notification types supported by the system.
 */
public enum NotificationType {
    
    /**
     * Email notifications - typically for important updates.
     */
    EMAIL("email"),
    
    /**
     * Slack notifications - for team collaboration.
     */
    SLACK("slack"),
    
    /**
     * Webhook notifications - for system integrations.
     */
    WEBHOOK("webhook"),
    
    /**
     * In-app notifications - for user interface notifications.
     */
    IN_APP("in_app"),
    
    /**
     * SMS notifications - for urgent alerts.
     */
    SMS("sms"),
    
    /**
     * Console/log notifications - for development and debugging.
     */
    CONSOLE("console");
    
    private final String channelId;
    
    NotificationType(String channelId) {
        this.channelId = channelId;
    }
    
    public String getChannelId() {
        return channelId;
    }
    
    /**
     * Returns whether this notification type supports rich content (HTML, markdown).
     */
    public boolean supportsRichContent() {
        return this == EMAIL || this == SLACK || this == IN_APP;
    }
    
    /**
     * Returns whether this notification type is typically synchronous.
     */
    public boolean isSynchronous() {
        return this == CONSOLE || this == IN_APP;
    }
    
    /**
     * Returns the default priority for this notification type.
     */
    public NotificationPriority getDefaultPriority() {
        return switch (this) {
            case SMS -> NotificationPriority.HIGH;
            case EMAIL, WEBHOOK -> NotificationPriority.MEDIUM;
            case SLACK, IN_APP, CONSOLE -> NotificationPriority.LOW;
        };
    }
    
    /**
     * Notification priority levels.
     */
    public enum NotificationPriority {
        HIGH, MEDIUM, LOW
    }
}