# Configuration Guide

This document provides comprehensive configuration options for the Content Approval Workflow System.

## Overview

The application uses Quarkus configuration management, which supports:
- **application.properties** files
- **Environment variables**
- **System properties**
- **Configuration profiles** (dev, test, prod)

## Application Properties

### Core Configuration

The main configuration file is located at `content-approval/src/main/resources/application.properties`.

#### Database Configuration

```properties
# Database connection
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=content_user
quarkus.datasource.password=content_pass
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/content_db

# Connection pool settings
quarkus.datasource.jdbc.initial-size=5
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=PT30S
quarkus.datasource.jdbc.leak-detection-interval=PT5M
```

#### Flyway Migration

```properties
# Migration settings
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.clean-at-start=false
quarkus.flyway.locations=db/migration
```

#### jOOQ Configuration

```properties
# jOOQ SQL dialect
quarkus.jooq.dialect=MYSQL
```

#### Temporal Configuration

```properties
# Temporal service connection
quarkus.temporal.namespace=content-approval
quarkus.temporal.service-url=localhost:7233
quarkus.temporal.start-workers=true

# Worker configuration
quarkus.temporal.worker.max-concurrent-activity-execution-size=100
quarkus.temporal.worker.max-concurrent-workflow-task-execution-size=50
quarkus.temporal.worker.max-concurrent-local-activity-execution-size=200

# Temporal connection settings
quarkus.temporal.connection.enable-https=false
quarkus.temporal.connection.max-grpc-inbound-message-size=52428800
```

#### HTTP Configuration

```properties
# Server settings
quarkus.http.port=8088
quarkus.http.host=0.0.0.0
quarkus.http.cors=true
quarkus.http.cors.origins=*
quarkus.http.cors.headers=*
quarkus.http.cors.methods=*
```

#### OpenAPI Configuration

```properties
# API documentation
quarkus.smallrye-openapi.info-title=Content Approval API
quarkus.smallrye-openapi.info-version=1.0.0
quarkus.smallrye-openapi.info-description=Content approval workflow system with Temporal orchestration
quarkus.smallrye-openapi.path=/q/openapi
quarkus.swagger-ui.path=/q/swagger-ui
quarkus.swagger-ui.always-include=true
```

#### Health Checks

```properties
# Health check configuration
quarkus.smallrye-health.root-path=/q/health
quarkus.smallrye-health.liveness-path=/q/health/live
quarkus.smallrye-health.readiness-path=/q/health/ready
quarkus.smallrye-health.group.ready.include=datasource
```

#### Logging Configuration

```properties
# Logging levels
quarkus.log.level=INFO
quarkus.log.category."com.wcygan.contentapproval".level=DEBUG
quarkus.log.category."io.temporal".level=INFO
quarkus.log.category."org.jooq".level=INFO

# Log format
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
quarkus.log.console.color=true

# File logging (optional)
# quarkus.log.file.enable=true
# quarkus.log.file.path=/var/log/content-approval.log
# quarkus.log.file.level=DEBUG
# quarkus.log.file.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n
```

## Environment Variables

All configuration properties can be overridden using environment variables. The naming convention converts property names to uppercase and replaces dots with underscores.

### Database Environment Variables

| Environment Variable | Property | Description | Default |
|---------------------|----------|-------------|---------|
| `QUARKUS_DATASOURCE_JDBC_URL` | `quarkus.datasource.jdbc.url` | Database connection URL | `jdbc:mysql://localhost:3306/content_db` |
| `QUARKUS_DATASOURCE_USERNAME` | `quarkus.datasource.username` | Database username | `content_user` |
| `QUARKUS_DATASOURCE_PASSWORD` | `quarkus.datasource.password` | Database password | `content_pass` |
| `QUARKUS_DATASOURCE_JDBC_MAX_SIZE` | `quarkus.datasource.jdbc.max-size` | Maximum connection pool size | `20` |

### Temporal Environment Variables

| Environment Variable | Property | Description | Default |
|---------------------|----------|-------------|---------|
| `QUARKUS_TEMPORAL_SERVICE_URL` | `quarkus.temporal.service-url` | Temporal service endpoint | `localhost:7233` |
| `QUARKUS_TEMPORAL_NAMESPACE` | `quarkus.temporal.namespace` | Temporal namespace | `content-approval` |
| `QUARKUS_TEMPORAL_START_WORKERS` | `quarkus.temporal.start-workers` | Auto-start workers | `true` |

### Application Environment Variables

| Environment Variable | Property | Description | Default |
|---------------------|----------|-------------|---------|
| `QUARKUS_HTTP_PORT` | `quarkus.http.port` | HTTP server port | `8088` |
| `QUARKUS_HTTP_HOST` | `quarkus.http.host` | HTTP server host | `0.0.0.0` |
| `QUARKUS_LOG_LEVEL` | `quarkus.log.level` | Global log level | `INFO` |

## Configuration Profiles

### Development Profile

File: `application-dev.properties`

```properties
# Development-specific settings
quarkus.log.category."com.wcygan.contentapproval".level=DEBUG
quarkus.http.cors=true
quarkus.swagger-ui.always-include=true

# Development database (can use H2 for faster startup)
# quarkus.datasource.db-kind=h2
# quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb
```

### Test Profile

File: `application-test.properties`

```properties
# Test-specific settings
quarkus.datasource.db-kind=mysql
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:${test.mysql.port}/test
quarkus.log.level=WARN
quarkus.log.category."com.wcygan.contentapproval".level=INFO

# Temporal test settings
quarkus.temporal.namespace=test-content-approval
```

### Production Profile

File: `application-prod.properties`

```properties
# Production-specific settings
quarkus.log.level=INFO
quarkus.log.category."com.wcygan.contentapproval".level=INFO
quarkus.log.category."ROOT".level=WARN

# Security settings
quarkus.http.cors=false
quarkus.swagger-ui.always-include=false

# Performance settings
quarkus.datasource.jdbc.max-size=50
quarkus.temporal.worker.max-concurrent-activity-execution-size=200

# Health check settings
quarkus.smallrye-health.group.ready.include=datasource,temporal
```

## Docker Configuration

### Environment File

Create a `.env` file for Docker Compose:

```bash
# Database settings
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_DATABASE=content_db
MYSQL_USER=content_user
MYSQL_PASSWORD=content_pass

# Application settings
APP_HTTP_PORT=8088
APP_LOG_LEVEL=INFO

# Temporal settings
TEMPORAL_NAMESPACE=content-approval
TEMPORAL_UI_PORT=8081
```

### Docker Compose Override

File: `docker-compose.override.yml`

```yaml
version: '3.8'
services:
  content-approval:
    environment:
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql:3306/${MYSQL_DATABASE}
      - QUARKUS_DATASOURCE_USERNAME=${MYSQL_USER}
      - QUARKUS_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}
      - QUARKUS_TEMPORAL_SERVICE_URL=temporal:7233
      - QUARKUS_HTTP_PORT=${APP_HTTP_PORT}
      - QUARKUS_LOG_LEVEL=${APP_LOG_LEVEL}
    ports:
      - "${APP_HTTP_PORT}:8088"
```

## Advanced Configuration

### Custom Temporal Configuration

```java
@ApplicationScoped
public class TemporalConfiguration {
    
    @ConfigProperty(name = "quarkus.temporal.connection.max-grpc-inbound-message-size")
    int maxMessageSize;
    
    @ConfigProperty(name = "quarkus.temporal.worker.max-concurrent-activity-execution-size")
    int maxConcurrentActivities;
    
    @Produces
    @ApplicationScoped
    public WorkerOptions customWorkerOptions() {
        return WorkerOptions.newBuilder()
            .setMaxConcurrentActivityExecutionSize(maxConcurrentActivities)
            .setMaxConcurrentWorkflowTaskExecutionSize(50)
            .setMaxConcurrentLocalActivityExecutionSize(200)
            .build();
    }
}
```

### Database Connection Pool Tuning

```properties
# Connection pool optimization
quarkus.datasource.jdbc.initial-size=10
quarkus.datasource.jdbc.min-size=10
quarkus.datasource.jdbc.max-size=100
quarkus.datasource.jdbc.acquisition-timeout=PT30S
quarkus.datasource.jdbc.leak-detection-interval=PT10M
quarkus.datasource.jdbc.idle-removal-interval=PT5M
quarkus.datasource.jdbc.max-lifetime=PT30M
quarkus.datasource.jdbc.transaction-isolation-level=read-committed
```

### SSL/TLS Configuration

For production deployments with SSL:

```properties
# HTTPS configuration
quarkus.http.ssl.certificate.files=/path/to/certificate.pem
quarkus.http.ssl.certificate.key-files=/path/to/private-key.pem
quarkus.http.insecure-requests=redirect

# Temporal SSL configuration
quarkus.temporal.connection.enable-https=true
quarkus.temporal.connection.tls-cert-path=/path/to/temporal-cert.pem
quarkus.temporal.connection.tls-key-path=/path/to/temporal-key.pem
```

## Configuration Validation

### Required Properties

The following properties must be set for the application to start:

- `quarkus.datasource.jdbc.url`
- `quarkus.datasource.username`
- `quarkus.datasource.password`
- `quarkus.temporal.service-url`
- `quarkus.temporal.namespace`

### Configuration Checks

The application performs validation on startup:

1. **Database connectivity** - Verifies database connection
2. **Temporal connectivity** - Checks Temporal service availability
3. **Required properties** - Validates all required configuration
4. **Port availability** - Ensures HTTP port is available

## Monitoring Configuration

### Metrics Configuration

```properties
# Enable metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.registry-enabled-default=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

### Tracing Configuration

```properties
# Enable distributed tracing
quarkus.opentelemetry.enabled=true
quarkus.opentelemetry.tracer.exporter.otlp.endpoint=http://jaeger:14250
quarkus.opentelemetry.tracer.exporter.otlp.headers=authorization=Bearer ${JAEGER_API_KEY}
```

## Troubleshooting Configuration

### Common Issues

1. **Database Connection Failures**
   ```bash
   # Check database connectivity
   mysql -h localhost -P 3306 -u content_user -p content_db
   ```

2. **Temporal Connection Issues**
   ```bash
   # Check Temporal service
   curl http://localhost:7233/api/v1/namespaces
   ```

3. **Port Conflicts**
   ```bash
   # Check port availability
   netstat -tulpn | grep 8088
   ```

### Debug Configuration

Enable debug logging for configuration issues:

```properties
quarkus.log.category."io.quarkus.runtime.configuration".level=DEBUG
quarkus.log.category."io.smallrye.config".level=DEBUG
```

### Configuration Dump

To see all effective configuration:

```bash
# Runtime configuration endpoint
curl http://localhost:8088/q/info
```

## Security Considerations

1. **Sensitive Data**: Never commit passwords or secrets to version control
2. **Environment Variables**: Use environment variables for sensitive configuration
3. **Secrets Management**: Consider using tools like HashiCorp Vault or Kubernetes secrets
4. **Network Security**: Configure appropriate firewall rules and network policies
5. **SSL/TLS**: Always use HTTPS in production environments

## Configuration Best Practices

1. **Use profiles** for environment-specific settings
2. **Externalize secrets** using environment variables or secret management
3. **Document changes** to configuration files
4. **Validate configuration** in CI/CD pipelines
5. **Monitor configuration** changes in production
6. **Use configuration management** tools for complex deployments