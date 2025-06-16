# Integration Tests

This directory contains comprehensive integration tests for the Content Approval System that verify the complete functionality of Temporal workflows with real services.

## Test Categories

### 1. TemporalServiceIntegrationTest
Tests real Temporal service integration including:
- Complete workflow execution with real Temporal service
- Signal handling (approve, reject, request changes)
- Query methods during workflow execution
- Workflow history and metadata
- Concurrent workflow execution

### 2. ActivityFailureIntegrationTest
Tests activity failure scenarios and error handling:
- Content validation failures with auto-rejection
- Activity retry behavior under transient failures
- Database transaction rollback scenarios
- Concurrent workflow database integrity
- Activity timeout handling
- Partial workflow failure recovery

### 3. PerformanceIntegrationTest
Tests system performance under load:
- High volume workflow execution (20+ concurrent workflows)
- Memory usage monitoring during concurrent execution
- Latency measurements under normal load
- Database connection pool behavior under stress
- Throughput and response time analysis

### 4. ResilienceIntegrationTest
Tests system resilience and recovery:
- Workflow recovery after worker restart simulation
- High failure rate scenario handling
- Workflow timeout handling and cleanup
- Concurrent signal handling resilience
- Resource exhaustion recovery testing

### 5. RestApiIntegrationTest
Tests complete REST API integration:
- End-to-end content submission workflow
- Content approval/rejection via REST API
- Request changes workflow
- Invalid request handling
- Concurrent API request handling
- Error response validation

## Prerequisites

### Infrastructure Requirements
Before running integration tests, ensure the following services are running:

```bash
# Start infrastructure services
deno task up

# Verify services are running
deno task status
```

Required services:
- **MySQL Database** (port 3306) - for persistent storage
- **Temporal Service** (port 7233) - for workflow orchestration
- **Temporal UI** (port 8081) - for workflow monitoring

### Docker Requirements
Integration tests use MySQL TestContainers which require Docker:
- Docker Desktop or Docker Engine must be running
- Sufficient resources allocated (recommended: 4GB+ RAM)

## Running Integration Tests

### All Integration Tests
```bash
# Run all integration tests (requires infrastructure)
deno task test --integration

# Or using Maven directly
mvn test -Dtest="*IntegrationTest"
```

### Individual Test Categories
```bash
# Temporal service integration
mvn test -Dtest="TemporalServiceIntegrationTest"

# Activity failure scenarios
mvn test -Dtest="ActivityFailureIntegrationTest"

# Performance testing
mvn test -Dtest="PerformanceIntegrationTest"

# Resilience testing
mvn test -Dtest="ResilienceIntegrationTest"

# REST API integration
mvn test -Dtest="RestApiIntegrationTest"
```

### Specific Test Methods
```bash
# Single test method
mvn test -Dtest="TemporalServiceIntegrationTest#testWorkflowExecutionWithRealTemporalService"
```

## Test Data Management

### Database Isolation
- Each test class uses TestContainers with isolated MySQL instances
- Test data is automatically cleaned up in `@BeforeEach` methods
- Database schema is created via Flyway migrations

### Test Content Patterns
- Author IDs follow pattern: `{category}-test-{specific}`
- Content IDs are generated automatically
- Workflow IDs include timestamps for uniqueness

### Cleanup Strategy
```java
@BeforeEach
@Transactional
void setUp() {
    // Clean up test data from previous runs
    dsl.deleteFrom(CONTENT)
        .where(CONTENT.AUTHOR_ID.like("test-pattern-%"))
        .execute();
}
```

## Test Execution Strategy

### Parallel vs Sequential
- **Unit tests**: Run in parallel for speed
- **Integration tests**: Run sequentially to avoid resource contention
- **TestContainers**: Each test class gets isolated database

### Resource Management
- Database connections are pooled and managed by Quarkus
- Temporal workers are shared across tests in the same JVM
- Memory usage is monitored in performance tests

### Timeout Configuration
- Workflow execution timeout: 5-10 minutes per test
- Activity timeout: 30 seconds - 5 minutes
- Test method timeout: Varies by test complexity

## Performance Benchmarks

### Expected Performance Metrics
- **Throughput**: > 0.1 workflows/second under load
- **Latency**: < 30 seconds average, < 60 seconds P95
- **Memory**: < 10MB per workflow
- **Concurrency**: Support 20+ concurrent workflows

### Resource Limits
- **Maximum concurrent workflows**: 30 (stress testing)
- **Database connection pool**: 20 connections
- **Memory limit**: 2x baseline memory usage
- **Execution timeout**: 5 minutes per workflow

## Troubleshooting

### Common Issues

#### Temporal Service Connection
```
Error: UNAVAILABLE: io exception
```
**Solution**: Ensure Temporal service is running on port 7233
```bash
deno task up
deno task status
```

#### TestContainers Issues
```
Error: Could not find a valid Docker environment
```
**Solution**: Ensure Docker is running and accessible
```bash
docker info
```

#### Memory Issues
```
Error: OutOfMemoryError during concurrent tests
```
**Solution**: Increase JVM heap size
```bash
export MAVEN_OPTS="-Xmx4g"
mvn test
```

#### Database Connection Timeout
```
Error: Connection timeout
```
**Solution**: Check MySQL service status and connection limits

### Debug Mode
```bash
# Enable debug logging
mvn test -Dtest="TestClass" -X

# Enable Temporal debug logging
mvn test -Dquarkus.log.category."io.temporal".level=DEBUG
```

## Test Coverage

### Workflow Scenarios
- ✅ Complete approval workflow (validation → review → approval → publish)
- ✅ Auto-rejection (validation failure)
- ✅ Manual rejection with reason
- ✅ Changes requested workflow
- ✅ Timeout handling
- ✅ Signal processing
- ✅ Query methods

### Failure Scenarios
- ✅ Activity failures and retries
- ✅ Database transaction rollbacks
- ✅ Concurrent execution conflicts
- ✅ Resource exhaustion
- ✅ Worker restarts
- ✅ Network timeouts

### Performance Scenarios
- ✅ High volume execution (20+ workflows)
- ✅ Memory usage monitoring
- ✅ Latency measurement
- ✅ Database connection pooling
- ✅ Concurrent signal handling

### API Integration
- ✅ REST endpoint functionality
- ✅ Request validation
- ✅ Error handling
- ✅ Concurrent API access
- ✅ Signal delivery

## Integration with CI/CD

### GitHub Actions
```yaml
- name: Run Integration Tests
  run: |
    deno task up
    deno task test --integration
    deno task down
```

### Local Development
```bash
# Quick integration test cycle
deno task up && deno task test --integration ; deno task down
```

### Test Reports
- **Surefire Reports**: `target/surefire-reports/`
- **Coverage Reports**: `target/site/jacoco/`
- **Test Logs**: Console output and log files

These integration tests provide comprehensive coverage of the Content Approval System's functionality, ensuring that all components work correctly together in realistic scenarios.