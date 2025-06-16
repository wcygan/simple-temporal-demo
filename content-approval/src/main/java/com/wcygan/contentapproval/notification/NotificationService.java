package com.wcygan.contentapproval.notification;

import com.wcygan.contentapproval.exception.NotificationException;
import com.wcygan.contentapproval.notification.impl.ConsoleNotificationChannel;
import com.wcygan.contentapproval.notification.impl.EmailNotificationChannel;
import com.wcygan.contentapproval.notification.impl.SlackNotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Central notification service that orchestrates different notification channels.
 * Uses the Strategy pattern to route notifications to appropriate channels.
 */
@ApplicationScoped
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Inject
    ConsoleNotificationChannel consoleChannel;
    
    @Inject
    EmailNotificationChannel emailChannel;
    
    @Inject
    SlackNotificationChannel slackChannel;
    
    @ConfigProperty(name = "notification.fallback.enabled", defaultValue = "true")
    boolean fallbackEnabled;
    
    @ConfigProperty(name = "notification.parallel.enabled", defaultValue = "true")
    boolean parallelEnabled;
    
    private final Map<NotificationType, NotificationChannel> channels = new HashMap<>();
    
    /**
     * Initialize the notification channels map.
     */
    void initializeChannels() {
        if (channels.isEmpty()) {
            channels.put(NotificationType.CONSOLE, consoleChannel);
            channels.put(NotificationType.EMAIL, emailChannel);
            channels.put(NotificationType.SLACK, slackChannel);
        }
    }
    
    /**
     * Sends a single notification using the specified channel.
     */
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) throws NotificationException {
        initializeChannels();
        
        NotificationChannel channel = channels.get(request.getType());
        if (channel == null) {
            throw new NotificationException("No channel available for notification type: " + request.getType());
        }
        
        if (!channel.isAvailable()) {
            if (fallbackEnabled) {
                logger.warn("Channel {} unavailable, falling back to console", request.getType());
                return consoleChannel.sendAsync(request);
            } else {
                throw new NotificationException("Channel " + request.getType() + " is not available");
            }
        }
        
        logger.debug("Sending notification via {}: {}", request.getType(), request);
        return channel.sendAsync(request);
    }
    
    /**
     * Sends notifications to multiple channels for redundancy.
     */
    public CompletableFuture<List<NotificationResult>> sendToMultipleChannels(
            String recipient, String subject, String message, NotificationType... types) {
        
        List<CompletableFuture<NotificationResult>> futures = new ArrayList<>();
        
        for (NotificationType type : types) {
            try {
                NotificationRequest request = new NotificationRequest(recipient, subject, message, type);
                futures.add(sendAsync(request));
            } catch (NotificationException e) {
                logger.error("Failed to queue notification for type {}: {}", type, e.getMessage());
                // Add failed result to maintain order
                futures.add(CompletableFuture.completedFuture(
                    NotificationResult.failure("Failed to queue: " + e.getMessage())
                ));
            }
        }
        
        if (parallelEnabled) {
            // Send all notifications in parallel
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
        } else {
            // Send notifications sequentially
            return futures.stream()
                .reduce(CompletableFuture.completedFuture(new ArrayList<NotificationResult>()),
                    (acc, future) -> acc.thenCompose(list -> 
                        future.thenApply(result -> {
                            list.add(result);
                            return list;
                        })),
                    (f1, f2) -> f1.thenCombine(f2, (list1, list2) -> {
                        list1.addAll(list2);
                        return list1;
                    }));
        }
    }
    
    /**
     * Convenience method for author notifications (typically email + console).
     */
    public CompletableFuture<List<NotificationResult>> notifyAuthor(String authorId, String subject, String message) {
        return sendToMultipleChannels(authorId, subject, message, 
            NotificationType.EMAIL, NotificationType.CONSOLE);
    }
    
    /**
     * Convenience method for reviewer notifications (typically Slack + email).
     */
    public CompletableFuture<List<NotificationResult>> notifyReviewers(String reviewerGroup, String subject, String message) {
        return sendToMultipleChannels(reviewerGroup, subject, message, 
            NotificationType.SLACK, NotificationType.EMAIL, NotificationType.CONSOLE);
    }
    
    /**
     * Convenience method for admin notifications (typically multiple channels).
     */
    public CompletableFuture<List<NotificationResult>> notifyAdministrators(String subject, String message) {
        return sendToMultipleChannels("admins", subject, message, 
            NotificationType.SLACK, NotificationType.EMAIL, NotificationType.CONSOLE);
    }
    
    /**
     * Gets all available notification channels.
     */
    public List<NotificationChannel> getAvailableChannels() {
        initializeChannels();
        return channels.values().stream()
            .filter(NotificationChannel::isAvailable)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the status of all notification channels.
     */
    public Map<NotificationType, Boolean> getChannelStatus() {
        initializeChannels();
        return channels.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().isAvailable()
            ));
    }
    
    /**
     * Sends a notification synchronously (blocks until completion).
     */
    public NotificationResult send(NotificationRequest request) throws NotificationException {
        try {
            return sendAsync(request).get();
        } catch (Exception e) {
            throw new NotificationException("Synchronous notification failed", e);
        }
    }
}