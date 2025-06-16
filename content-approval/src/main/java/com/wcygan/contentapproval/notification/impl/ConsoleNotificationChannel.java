package com.wcygan.contentapproval.notification.impl;

import com.wcygan.contentapproval.exception.NotificationException;
import com.wcygan.contentapproval.notification.NotificationChannel;
import com.wcygan.contentapproval.notification.NotificationRequest;
import com.wcygan.contentapproval.notification.NotificationResult;
import com.wcygan.contentapproval.notification.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Console/logging notification channel implementation.
 * Outputs notifications to the application log for development and debugging.
 */
@ApplicationScoped
public class ConsoleNotificationChannel implements NotificationChannel {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsoleNotificationChannel.class);
    
    @Override
    public NotificationType getNotificationType() {
        return NotificationType.CONSOLE;
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) throws NotificationException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = UUID.randomUUID().toString();
                
                // Format and log the notification
                String formattedMessage = formatNotification(request);
                logger.info("📧 CONSOLE NOTIFICATION [{}] {}", messageId, formattedMessage);
                
                // Add metadata about the console output
                Map<String, Object> metadata = Map.of(
                    "channel", "console",
                    "formatted", true,
                    "logLevel", "INFO"
                );
                
                return NotificationResult.success(messageId, metadata);
                
            } catch (Exception e) {
                logger.error("Failed to send console notification: {}", request, e);
                return NotificationResult.failure("Console notification failed: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Console is always available
    }
    
    @Override
    public long getEstimatedDeliveryTimeMs() {
        return 10; // Immediate console output
    }
    
    /**
     * Formats the notification for console output.
     */
    private String formatNotification(NotificationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("To: ").append(request.getRecipient());
        sb.append(" | Subject: ").append(request.getSubject());
        sb.append(" | Message: ").append(request.getMessage());
        
        // Add metadata if present
        if (!request.getMetadata().isEmpty()) {
            sb.append(" | Metadata: ").append(request.getMetadata());
        }
        
        return sb.toString();
    }
}