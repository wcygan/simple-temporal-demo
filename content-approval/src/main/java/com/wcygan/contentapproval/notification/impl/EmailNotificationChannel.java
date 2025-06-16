package com.wcygan.contentapproval.notification.impl;

import com.wcygan.contentapproval.exception.NotificationException;
import com.wcygan.contentapproval.notification.NotificationChannel;
import com.wcygan.contentapproval.notification.NotificationRequest;
import com.wcygan.contentapproval.notification.NotificationResult;
import com.wcygan.contentapproval.notification.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Email notification channel implementation.
 * Currently simulates email sending with configurable delays.
 * In production, this would integrate with services like SendGrid, AWS SES, etc.
 */
@ApplicationScoped
public class EmailNotificationChannel implements NotificationChannel {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationChannel.class);
    
    @ConfigProperty(name = "notification.email.enabled", defaultValue = "true")
    boolean emailEnabled;
    
    @ConfigProperty(name = "notification.email.simulation.delay.ms", defaultValue = "100")
    long simulationDelayMs;
    
    @ConfigProperty(name = "notification.email.from", defaultValue = "noreply@contentapproval.com")
    String fromAddress;
    
    @Override
    public NotificationType getNotificationType() {
        return NotificationType.EMAIL;
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) throws NotificationException {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(
                NotificationResult.failure("Email notifications are disabled")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = UUID.randomUUID().toString();
                
                logger.info("📧 Sending email notification [{}] to: {}", messageId, request.getRecipient());
                
                // Simulate email sending delay
                Thread.sleep(simulationDelayMs);
                
                // In production, this would:
                // 1. Validate email address
                // 2. Format HTML/text content
                // 3. Send via email service API
                // 4. Handle delivery confirmations
                
                logger.info("✅ Email sent successfully [{}]: Subject='{}' to='{}'", 
                    messageId, request.getSubject(), request.getRecipient());
                
                Map<String, Object> metadata = Map.of(
                    "channel", "email",
                    "fromAddress", fromAddress,
                    "deliveryAttempts", 1,
                    "simulationMode", true
                );
                
                return NotificationResult.success(messageId, metadata);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Email notification interrupted: {}", request, e);
                return NotificationResult.failure("Email sending was interrupted");
            } catch (Exception e) {
                logger.error("Failed to send email notification: {}", request, e);
                return NotificationResult.failure("Email sending failed: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return emailEnabled;
    }
    
    @Override
    public long getEstimatedDeliveryTimeMs() {
        return simulationDelayMs + 200; // Simulation delay + processing overhead
    }
}