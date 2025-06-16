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
 * Slack notification channel implementation.
 * Currently simulates Slack messaging with configurable delays.
 * In production, this would integrate with Slack API using webhooks or bot tokens.
 */
@ApplicationScoped
public class SlackNotificationChannel implements NotificationChannel {
    
    private static final Logger logger = LoggerFactory.getLogger(SlackNotificationChannel.class);
    
    @ConfigProperty(name = "notification.slack.enabled", defaultValue = "false")
    boolean slackEnabled;
    
    @ConfigProperty(name = "notification.slack.simulation.delay.ms", defaultValue = "150")
    long simulationDelayMs;
    
    @ConfigProperty(name = "notification.slack.default.channel", defaultValue = "#content-approval")
    String defaultChannel;
    
    @ConfigProperty(name = "notification.slack.bot.name", defaultValue = "ContentBot")
    String botName;
    
    @Override
    public NotificationType getNotificationType() {
        return NotificationType.SLACK;
    }
    
    @Override
    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) throws NotificationException {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(
                NotificationResult.failure("Slack notifications are disabled")
            );
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = UUID.randomUUID().toString();
                String channel = determineChannel(request);
                
                logger.info("💬 Sending Slack notification [{}] to channel: {}", messageId, channel);
                
                // Simulate Slack API call delay
                Thread.sleep(simulationDelayMs);
                
                // Format message for Slack (with emoji and markdown)
                String slackMessage = formatSlackMessage(request);
                
                // In production, this would:
                // 1. Call Slack API with webhook or bot token
                // 2. Handle rate limiting
                // 3. Support rich formatting, attachments, blocks
                // 4. Handle @mentions and channel notifications
                
                logger.info("✅ Slack message sent [{}]: {} -> {}", 
                    messageId, channel, slackMessage);
                
                Map<String, Object> metadata = Map.of(
                    "channel", "slack",
                    "slackChannel", channel,
                    "botName", botName,
                    "formatted", true,
                    "simulationMode", true
                );
                
                return NotificationResult.success(messageId, metadata);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Slack notification interrupted: {}", request, e);
                return NotificationResult.failure("Slack notification was interrupted");
            } catch (Exception e) {
                logger.error("Failed to send Slack notification: {}", request, e);
                return NotificationResult.failure("Slack notification failed: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return slackEnabled;
    }
    
    @Override
    public long getEstimatedDeliveryTimeMs() {
        return simulationDelayMs + 100; // API call delay + processing
    }
    
    /**
     * Determines the Slack channel to send to based on the request.
     */
    private String determineChannel(NotificationRequest request) {
        // Check if channel is specified in metadata
        Object channelMetadata = request.getMetadata("slackChannel");
        if (channelMetadata instanceof String) {
            return (String) channelMetadata;
        }
        
        // Use recipient if it looks like a channel (starts with #)
        String recipient = request.getRecipient();
        if (recipient.startsWith("#") || recipient.startsWith("@")) {
            return recipient;
        }
        
        // Default channel
        return defaultChannel;
    }
    
    /**
     * Formats the message for Slack with emoji and basic formatting.
     */
    private String formatSlackMessage(NotificationRequest request) {
        String emoji = getEmojiForSubject(request.getSubject());
        return String.format("%s *%s*\\n%s", 
            emoji, 
            request.getSubject(), 
            request.getMessage()
        );
    }
    
    /**
     * Selects appropriate emoji based on the notification subject.
     */
    private String getEmojiForSubject(String subject) {
        String lower = subject.toLowerCase();
        if (lower.contains("approved")) return "✅";
        if (lower.contains("rejected")) return "❌";
        if (lower.contains("changes") || lower.contains("request")) return "📝";
        if (lower.contains("review")) return "👀";
        if (lower.contains("published")) return "🚀";
        return "📢"; // Default notification emoji
    }
}