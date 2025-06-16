package com.wcygan.contentapproval.dto;

import java.time.Duration;

/**
 * Configuration data for content approval workflows.
 * This record is serializable and can be passed to Temporal workflows.
 */
public record WorkflowConfiguration(
    Duration reviewTimeout,
    boolean validationEnabled,
    boolean autoPublishEnabled,
    boolean notificationEnabled
) {
    
    /**
     * Creates a default configuration with standard values.
     */
    public static WorkflowConfiguration defaultConfiguration() {
        return new WorkflowConfiguration(
            Duration.ofDays(7), // 7 days review timeout
            true,               // validation enabled
            true,               // auto-publish enabled
            true                // notifications enabled
        );
    }
    
    /**
     * Gets the review timeout in seconds for backwards compatibility.
     */
    public long getReviewTimeoutSeconds() {
        return reviewTimeout.getSeconds();
    }
}