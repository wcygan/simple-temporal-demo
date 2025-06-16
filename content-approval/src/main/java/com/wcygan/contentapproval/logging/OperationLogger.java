package com.wcygan.contentapproval.logging;

import org.slf4j.Logger;

/**
 * Utility class for standardized operational logging patterns.
 * Provides consistent formatting and context for business operations.
 */
public class OperationLogger {
    
    private final Logger logger;
    
    public OperationLogger(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Logs the start of an operation.
     */
    public void operationStarted(String operation, LogContext context) {
        logger.info("{} started {}", operation, context.build(""));
    }
    
    /**
     * Logs successful completion of an operation.
     */
    public void operationCompleted(String operation, LogContext context) {
        logger.info("{} completed {}", operation, context.build(""));
    }
    
    /**
     * Logs successful completion with duration.
     */
    public void operationCompleted(String operation, LogContext context, long durationMs) {
        logger.info("{} completed {}", operation, context.duration(durationMs).build(""));
    }
    
    /**
     * Logs operation failure.
     */
    public void operationFailed(String operation, LogContext context, String reason) {
        logger.warn("{} failed {}", operation, context.errorReason(reason).build(""));
    }
    
    /**
     * Logs operation failure with exception.
     */
    public void operationFailed(String operation, LogContext context, String reason, Throwable exception) {
        logger.error("{} failed {}", operation, context.errorReason(reason).build(""), exception);
    }
    
    /**
     * Logs business rule violation.
     */
    public void businessRuleViolation(String rule, LogContext context, String violation) {
        logger.warn("Business rule '{}' violated {}", rule, context.errorReason(violation).build(""));
    }
    
    /**
     * Logs security or audit event.
     */
    public void auditEvent(String event, LogContext context) {
        logger.info("AUDIT: {} {}", event, context.build(""));
    }
    
    /**
     * Logs performance metrics.
     */
    public void performanceMetric(String metric, LogContext context, Object value) {
        logger.info("METRIC: {} = {} {}", metric, value, context.build(""));
    }
    
    /**
     * Logs debug information with context.
     */
    public void debug(String message, LogContext context) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", message, context.build(""));
        }
    }
    
    /**
     * Logs detailed trace information.
     */
    public void trace(String message, LogContext context) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} {}", message, context.build(""));
        }
    }
    
    /**
     * Logs expensive operations that should only be logged at DEBUG level.
     */
    public void debugExpensive(String message, LogContext context, Object expensiveData) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {} data={}", message, context.build(""), expensiveData);
        }
    }
}