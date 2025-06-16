package com.wcygan.contentapproval.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for notification operations.
 * Handles sending notifications to authors, reviewers, and administrators.
 */
@ActivityInterface
public interface NotificationActivity {
    
    /**
     * Sends a notification to the content author.
     * 
     * @param authorId The ID of the author
     * @param subject The notification subject
     * @param message The notification message
     */
    @ActivityMethod
    void notifyAuthor(String authorId, String subject, String message);
    
    /**
     * Sends a notification to reviewers about new content.
     * 
     * @param contentId The ID of the content
     * @param authorId The ID of the author
     * @param message The notification message
     */
    @ActivityMethod
    void notifyReviewers(Long contentId, String authorId, String message);
    
    /**
     * Sends a notification to administrators about system events.
     * 
     * @param subject The notification subject
     * @param message The notification message
     */
    @ActivityMethod
    void notifyAdministrators(String subject, String message);
    
    /**
     * Sends an email notification.
     * 
     * @param recipient The email recipient
     * @param subject The email subject
     * @param body The email body
     */
    @ActivityMethod
    void sendEmail(String recipient, String subject, String body);
    
    /**
     * Sends a webhook notification.
     * 
     * @param webhookUrl The webhook URL
     * @param payload The notification payload as JSON
     */
    @ActivityMethod
    void sendWebhookNotification(String webhookUrl, String payload);
    
    /**
     * Sends a Slack notification to a channel.
     * 
     * @param channel The Slack channel
     * @param message The message to send
     */
    @ActivityMethod
    void sendSlackNotification(String channel, String message);
    
    /**
     * Creates a notification for in-app display.
     * 
     * @param userId The user ID to notify
     * @param type The notification type
     * @param title The notification title
     * @param message The notification message
     */
    @ActivityMethod
    void createInAppNotification(String userId, String type, String title, String message);
}