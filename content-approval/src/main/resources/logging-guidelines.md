# Logging Guidelines for Content Approval System

## Standard Log Level Usage

### TRACE
- **Purpose**: Very detailed debugging (disabled in production)
- **Usage**: Method entry/exit, detailed variable states
- **Example**: `opLogger.trace("Entering validateTitleInternal", context.field("titleLength", title.length()))`

### DEBUG  
- **Purpose**: Development debugging and detailed operations
- **Usage**: Entity operations, expensive data logging, detailed business logic flow
- **Example**: `opLogger.debug("Content entity mapping", context.field("entity", entity))`

### INFO
- **Purpose**: Business operations, successful completions, workflow state changes
- **Usage**: Major operations start/completion, audit events, workflow transitions
- **Example**: `opLogger.operationCompleted("Content submission", context)`

### WARN
- **Purpose**: Recoverable issues, business rule violations, fallback scenarios
- **Usage**: Invalid state transitions, configuration issues, retry attempts
- **Example**: `opLogger.businessRuleViolation("Content state", context, "Invalid transition")`

### ERROR
- **Purpose**: System failures, exceptions, critical business errors
- **Usage**: Database failures, external service errors, workflow failures
- **Example**: `opLogger.operationFailed("Database save", context, "Connection timeout", exception)`

## Required Context Information

### Always Include
- **contentId**: For all content-related operations
- **workflowId**: For all workflow operations  
- **userId**: For user-initiated actions
- **operation**: The business operation being performed

### Additional Context by Operation Type
- **Database Operations**: duration, affected rows, query type
- **Workflow Operations**: workflow state, configuration flags
- **Validation Operations**: validation rules, failure reasons
- **Notification Operations**: channel type, recipient count

## Log Message Patterns

### Operation Logging
```java
// Start
opLogger.operationStarted("Content validation", context);

// Success
opLogger.operationCompleted("Content validation", context, durationMs);

// Failure
opLogger.operationFailed("Content validation", context, "Validation rules failed", exception);
```

### Audit Logging
```java
// User actions
opLogger.auditEvent("Content approved by reviewer", context.userId(reviewerId).status("APPROVED"));

// System actions  
opLogger.auditEvent("Content auto-rejected due to timeout", context.status("REJECTED"));
```

### Business Rule Violations
```java
opLogger.businessRuleViolation("Content state transition", context, "Cannot approve rejected content");
```

### Performance Monitoring
```java
opLogger.performanceMetric("Database query time", context, queryTimeMs);
```

## Performance Considerations

### Expensive Operations
- Use `debugExpensive()` for object serialization
- Use parameterized logging for string concatenation
- Avoid logging large objects at INFO level

### Conditional Logging
```java
// Check log level before expensive operations
if (logger.isDebugEnabled()) {
    opLogger.debug("Detailed entity state", context.field("entity", expensiveToString()));
}
```

## Structured Logging Examples

### Content Submission
```java
LogContext context = LogContext.create()
    .contentId(contentId)
    .userId(authorId)
    .operation("submitContent")
    .field("titleLength", title.length())
    .field("contentLength", content.length());

opLogger.operationStarted("Content submission", context);
// ... business logic ...
opLogger.auditEvent("Content submitted for approval", context);
opLogger.operationCompleted("Content submission", context, duration);
```

### Workflow State Transitions
```java
LogContext context = LogContext.withContentAndWorkflow(contentId, workflowId)
    .operation("approveContent")
    .userId(reviewerId)
    .status(currentStatus);

opLogger.auditEvent("Content approval decision", context.field("decision", "approved"));
```

### Error Handling
```java
LogContext context = LogContext.withContentId(contentId)
    .operation("validateContent")
    .field("validationRule", ruleName);

try {
    // validation logic
    opLogger.operationCompleted("Content validation", context);
} catch (ValidationException e) {
    opLogger.operationFailed("Content validation", context, "Business rule violation", e);
} catch (Exception e) {
    opLogger.operationFailed("Content validation", context, "System error", e);
}
```

## Integration with Monitoring

### Correlation IDs
- Add correlation IDs to LogContext for distributed tracing
- Include request IDs in REST endpoints

### Metrics Collection
- Use performance metrics logging for monitoring dashboards
- Track business KPIs through audit events

### Alert-worthy Events
- All ERROR level logs should be alertable
- Business rule violations may require monitoring
- Performance degradation should trigger alerts

## Best Practices

1. **Consistency**: Use OperationLogger and LogContext throughout
2. **Context**: Always include relevant business context
3. **Performance**: Move expensive logging to DEBUG level
4. **Security**: Never log sensitive data (passwords, tokens, PII)
5. **Audit**: Log all business decisions and state changes
6. **Correlation**: Include IDs for tracing across services
7. **Standards**: Follow established message patterns