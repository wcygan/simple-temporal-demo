package com.wcygan.contentapproval.notification;

import com.wcygan.contentapproval.exception.NotificationException;

import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for notification channels.
 * Each implementation handles a specific notification method (email, Slack, webhook, etc.).
 */
public interface NotificationChannel {
    
    /**
     * Gets the notification type this channel handles.
     */
    NotificationType getNotificationType();
    
    /**
     * Sends a notification asynchronously.
     * 
     * @param request The notification request
     * @return CompletableFuture with the notification result
     * @throws NotificationException if the notification cannot be processed
     */
    CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) throws NotificationException;
    
    /**
     * Sends a notification synchronously.
     * Default implementation blocks on the async version.
     * 
     * @param request The notification request
     * @return The notification result
     * @throws NotificationException if the notification fails
     */
    default NotificationResult send(NotificationRequest request) throws NotificationException {
        try {
            return sendAsync(request).get();
        } catch (Exception e) {
            throw new NotificationException("Synchronous notification failed", e);
        }
    }
    
    /**
     * Returns whether this channel is available and configured.
     */
    boolean isAvailable();
    
    /**
     * Returns whether this channel supports the given notification request.
     */
    default boolean supports(NotificationRequest request) {
        return getNotificationType() == request.getType() && isAvailable();
    }
    
    /**
     * Gets the estimated delivery time for this channel.
     */
    default long getEstimatedDeliveryTimeMs() {
        return 1000; // Default 1 second
    }
}