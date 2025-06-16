package com.wcygan.contentapproval.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * Configuration service for workflow-related settings.
 * Centralizes configuration access and provides type-safe configuration values.
 */
@ApplicationScoped
public class WorkflowConfigurationService {
    
    @ConfigProperty(name = "content.approval.review.timeout.seconds", defaultValue = "604800")
    int reviewTimeoutSeconds; // 7 days default
    
    @ConfigProperty(name = "content.approval.task.queue", defaultValue = "content-approval")
    String taskQueue;
    
    @ConfigProperty(name = "content.approval.validation.enabled", defaultValue = "true")
    boolean validationEnabled;
    
    @ConfigProperty(name = "content.approval.auto.publish", defaultValue = "true")
    boolean autoPublishEnabled;
    
    @ConfigProperty(name = "content.approval.notification.enabled", defaultValue = "true")
    boolean notificationEnabled;
    
    @ConfigProperty(name = "content.approval.workflow.execution.timeout.days", defaultValue = "30")
    int workflowExecutionTimeoutDays;
    
    /**
     * Gets the review timeout as a Duration object.
     */
    public Duration getReviewTimeout() {
        return Duration.ofSeconds(reviewTimeoutSeconds);
    }
    
    /**
     * Gets the task queue name for workflow workers.
     */
    public String getTaskQueue() {
        return taskQueue;
    }
    
    /**
     * Returns whether content validation is enabled.
     */
    public boolean isValidationEnabled() {
        return validationEnabled;
    }
    
    /**
     * Returns whether auto-publishing is enabled for approved content.
     */
    public boolean isAutoPublishEnabled() {
        return autoPublishEnabled;
    }
    
    /**
     * Returns whether notifications are enabled.
     */
    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }
    
    /**
     * Gets the workflow execution timeout as a Duration object.
     */
    public Duration getWorkflowExecutionTimeout() {
        return Duration.ofDays(workflowExecutionTimeoutDays);
    }
    
    /**
     * Gets the review timeout in seconds (for backwards compatibility).
     */
    public int getReviewTimeoutSeconds() {
        return reviewTimeoutSeconds;
    }
}