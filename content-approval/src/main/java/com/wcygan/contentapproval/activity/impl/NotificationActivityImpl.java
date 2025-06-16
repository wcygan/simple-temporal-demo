package com.wcygan.contentapproval.activity.impl;

import com.wcygan.contentapproval.activity.NotificationActivity;
import com.wcygan.contentapproval.exception.NotificationException;
import com.wcygan.contentapproval.notification.NotificationRequest;
import com.wcygan.contentapproval.notification.NotificationResult;
import com.wcygan.contentapproval.notification.NotificationService;
import com.wcygan.contentapproval.notification.NotificationType;
import io.temporal.activity.Activity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of NotificationActivity.
 * Handles sending notifications through various channels.
 * This is a simplified implementation that logs notifications.
 * In production, this would integrate with email services, Slack, webhooks, etc.
 */
@ApplicationScoped
public class NotificationActivityImpl implements NotificationActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationActivityImpl.class);
    
    @Inject
    NotificationService notificationService;
    
    @Override
    public void notifyAuthor(String authorId, String subject, String message) {
        logger.info("Notifying author {} - Subject: {}", authorId, subject);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending author notification");
            
            // Use notification service to send via multiple channels
            List<NotificationResult> results = notificationService.notifyAuthor(authorId, subject, message).get();
            
            // Log results and check for failures
            boolean allSuccessful = true;
            for (NotificationResult result : results) {
                if (result.isSuccess()) {
                    logger.info("✅ Author notification sent via {}: messageId={}", 
                        getChannelFromMetadata(result), result.getMessageId());
                } else {
                    logger.error("❌ Author notification failed via {}: {}", 
                        getChannelFromMetadata(result), result.getErrorMessage());
                    allSuccessful = false;
                }
            }
            
            if (!allSuccessful) {
                logger.warn("Some author notifications failed for {}, but at least one succeeded", authorId);
            }
            
            logger.info("Author notification process completed for {} with {} results", authorId, results.size());
            
        } catch (NotificationException e) {
            logger.error("Notification service error for author {}", authorId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Author notification failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Author notification interrupted for {}", authorId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Author notification interrupted", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending author notification to {}", authorId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Author notification failed due to unexpected error", e);
        }
    }
    
    @Override
    public void notifyReviewers(Long contentId, String authorId, String message) {
        logger.info("Notifying reviewers about content {} from author {}", contentId, authorId);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending reviewer notifications");
            
            // Create enhanced message with content context
            String subject = "New Content Ready for Review";
            String enhancedMessage = String.format(
                "Content ID: %d submitted by %s requires review.\n\n%s", 
                contentId, authorId, message
            );
            
            // Use notification service for reviewer group
            String reviewerGroup = "content-reviewers";
            List<NotificationResult> results = notificationService
                .notifyReviewers(reviewerGroup, subject, enhancedMessage).get();
            
            // Log results and check for failures
            boolean allSuccessful = true;
            for (NotificationResult result : results) {
                if (result.isSuccess()) {
                    logger.info("✅ Reviewer notification sent via {}: messageId={}", 
                        getChannelFromMetadata(result), result.getMessageId());
                } else {
                    logger.error("❌ Reviewer notification failed via {}: {}", 
                        getChannelFromMetadata(result), result.getErrorMessage());
                    allSuccessful = false;
                }
            }
            
            if (!allSuccessful) {
                logger.warn("Some reviewer notifications failed for content {}, but at least one succeeded", contentId);
            }
            
            logger.info("Reviewer notification process completed for content {} with {} results", contentId, results.size());
            
        } catch (NotificationException e) {
            logger.error("Notification service error for content {}", contentId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Reviewer notification failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Reviewer notification interrupted for content {}", contentId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Reviewer notification interrupted", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending reviewer notifications for content {}", contentId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Reviewer notification failed due to unexpected error", e);
        }
    }
    
    /**
     * Helper method to extract channel name from notification result metadata.
     */
    private String getChannelFromMetadata(NotificationResult result) {
        Object channel = result.getMetadata("channel");
        return channel != null ? channel.toString() : "unknown";
    }
    
    @Override
    public void notifyAdministrators(String subject, String message) {
        logger.info("Notifying administrators - Subject: {}", subject);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending administrator notification");
            
            // Use notification service for admin notifications
            List<NotificationResult> results = notificationService
                .notifyAdministrators(subject, message).get();
            
            // Log results and check for failures
            boolean allSuccessful = true;
            for (NotificationResult result : results) {
                if (result.isSuccess()) {
                    logger.info("✅ Admin notification sent via {}: messageId={}", 
                        getChannelFromMetadata(result), result.getMessageId());
                } else {
                    logger.error("❌ Admin notification failed via {}: {}", 
                        getChannelFromMetadata(result), result.getErrorMessage());
                    allSuccessful = false;
                }
            }
            
            if (!allSuccessful) {
                logger.warn("Some admin notifications failed, but at least one succeeded");
            }
            
            logger.info("Admin notification process completed with {} results", results.size());
            
        } catch (NotificationException e) {
            logger.error("Notification service error for admin notification", e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Admin notification failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Admin notification interrupted", e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Admin notification interrupted", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending admin notification", e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Admin notification failed due to unexpected error", e);
        }
    }
    
    @Override
    public void sendEmail(String recipient, String subject, String body) {
        logger.info("Sending email to {} - Subject: {}", recipient, subject);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending email");
            
            // Use notification service for email sending
            NotificationRequest request = new NotificationRequest(recipient, subject, body, NotificationType.EMAIL);
            NotificationResult result = notificationService.send(request);
            
            if (result.isSuccess()) {
                logger.info("✅ Email sent successfully: messageId={}", result.getMessageId());
            } else {
                logger.error("❌ Email sending failed: {}", result.getErrorMessage());
                throw new com.wcygan.contentapproval.exception.NotificationException(
                    "Email sending failed: " + result.getErrorMessage());
            }
            
        } catch (NotificationException e) {
            logger.error("Email notification service error for {}", recipient, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error sending email to {}", recipient, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Email sending failed due to unexpected error", e);
        }
    }
    
    @Override
    public void sendWebhookNotification(String webhookUrl, String payload) {
        logger.info("Sending webhook notification to {}", webhookUrl);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending webhook notification");
            
            // Use notification service for webhook sending
            NotificationRequest request = new NotificationRequest(webhookUrl, "Webhook Notification", payload, NotificationType.WEBHOOK)
                .withMetadata("webhookUrl", webhookUrl)
                .withMetadata("payload", payload);
            NotificationResult result = notificationService.send(request);
            
            if (result.isSuccess()) {
                logger.info("✅ Webhook notification sent successfully: messageId={}", result.getMessageId());
            } else {
                logger.error("❌ Webhook notification failed: {}", result.getErrorMessage());
                throw new com.wcygan.contentapproval.exception.NotificationException(
                    "Webhook notification failed: " + result.getErrorMessage());
            }
            
        } catch (NotificationException e) {
            logger.error("Webhook notification service error for {}", webhookUrl, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error sending webhook notification to {}", webhookUrl, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Webhook notification failed due to unexpected error", e);
        }
    }
    
    @Override
    public void sendSlackNotification(String channel, String message) {
        logger.info("Sending Slack notification to channel {}", channel);
        
        try {
            Activity.getExecutionContext().heartbeat("Sending Slack notification");
            
            // Use notification service for Slack sending
            NotificationRequest request = new NotificationRequest(channel, "Slack Notification", message, NotificationType.SLACK)
                .withMetadata("slackChannel", channel);
            NotificationResult result = notificationService.send(request);
            
            if (result.isSuccess()) {
                logger.info("✅ Slack notification sent successfully: messageId={}", result.getMessageId());
            } else {
                logger.error("❌ Slack notification failed: {}", result.getErrorMessage());
                throw new com.wcygan.contentapproval.exception.NotificationException(
                    "Slack notification failed: " + result.getErrorMessage());
            }
            
        } catch (NotificationException e) {
            logger.error("Slack notification service error for channel {}", channel, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error sending Slack notification to {}", channel, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("Slack notification failed due to unexpected error", e);
        }
    }
    
    @Override
    public void createInAppNotification(String userId, String type, String title, String message) {
        logger.info("Creating in-app notification for user {} - Type: {}", userId, type);
        
        try {
            Activity.getExecutionContext().heartbeat("Creating in-app notification");
            
            // Use notification service for in-app notifications
            NotificationRequest request = new NotificationRequest(userId, title, message, NotificationType.IN_APP)
                .withMetadata("notificationType", type)
                .withMetadata("userId", userId);
            NotificationResult result = notificationService.send(request);
            
            if (result.isSuccess()) {
                logger.info("✅ In-app notification created successfully: messageId={}", result.getMessageId());
            } else {
                logger.error("❌ In-app notification creation failed: {}", result.getErrorMessage());
                throw new com.wcygan.contentapproval.exception.NotificationException(
                    "In-app notification creation failed: " + result.getErrorMessage());
            }
            
        } catch (NotificationException e) {
            logger.error("In-app notification service error for user {}", userId, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating in-app notification for user {}", userId, e);
            throw new com.wcygan.contentapproval.exception.NotificationException("In-app notification creation failed due to unexpected error", e);
        }
    }
}