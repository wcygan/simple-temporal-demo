# Content Approval Workflow System

A production-ready content approval system built with Java, Quarkus, and Temporal workflow orchestration.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.23.3-blue.svg)](https://quarkus.io/)
[![Temporal](https://img.shields.io/badge/Temporal-1.25.2-green.svg)](https://temporal.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

## Overview

This project demonstrates a comprehensive content approval workflow system that leverages Temporal for durable, fault-tolerant workflow orchestration. It showcases modern Java development practices with type-safe database access, comprehensive testing, and production-ready infrastructure.

### Key Features

- **Durable Workflows**: Content approval processes that survive system failures
- **REST API**: Complete CRUD operations with OpenAPI documentation
- **Type-Safe Database**: jOOQ integration with MySQL for compile-time query validation
- **Comprehensive Testing**: Unit tests with TestWorkflowEnvironment, activity failure testing, performance testing, resilience testing, and REST API integration tests with TestContainers
- **Production Ready**: Health checks, monitoring, and containerized deployment

## Quick Start

```bash
# Start infrastructure and development server
deno task up && deno task dev

# Or manually:
docker compose -f content-approval/docker-compose.yml up -d
cd content-approval && mvn quarkus:dev
```

The application will be available at:
- **API**: http://localhost:8088
- **Swagger UI**: http://localhost:8088/q/swagger-ui/
- **Temporal UI**: http://localhost:8081
- **Health Checks**: http://localhost:8088/q/health

## Architecture

### Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Framework** | Quarkus 3.23.3 | Cloud-native Java framework |
| **Orchestration** | Temporal 1.25.2 | Workflow engine for durable processes |
| **Database** | MySQL 8.0 | Primary data storage |
| **ORM** | jOOQ 2.1.0 | Type-safe SQL queries |
| **Migration** | Flyway | Database schema management |
| **Testing** | TestContainers | Integration testing with real databases |
| **Build** | Maven + Deno | Java compilation and task automation |

### Workflow States

```
DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED
   ↓         ↓             ↓
REJECTED  REJECTED  CHANGES_REQUESTED
```

### Project Structure

```
content-approval/
├── src/main/java/com/wcygan/contentapproval/
│   ├── resource/          # REST API endpoints
│   ├── service/           # Business logic layer
│   ├── workflow/          # Temporal workflow definitions
│   ├── activity/          # Temporal activity implementations
│   ├── dto/              # Data transfer objects
│   └── config/           # Configuration classes
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/     # Flyway SQL migrations
├── src/test/java/        # Comprehensive test suite
├── docker-compose.yml    # Infrastructure services
└── pom.xml              # Maven dependencies
```

## API Reference

### Submit Content for Approval

```bash
curl -X POST http://localhost:8088/content \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Getting Started with Temporal",
    "content": "Temporal is a developer-first platform for building resilient applications...",
    "authorId": "author123",
    "tags": ["temporal", "workflow", "java"]
  }'
```

### Check Content Status

```bash
curl http://localhost:8088/content/1/status
```

### Approve Content

```bash
curl -X POST "http://localhost:8088/content/1/approve?approverId=reviewer1&comments=Excellent%20article"
```

### Reject Content

```bash
curl -X POST "http://localhost:8088/content/1/reject?reviewerId=reviewer2&reason=Needs%20more%20examples"
```

## Development

### Prerequisites

- **Java 21+**: OpenJDK or Oracle JDK
- **Maven 3.9+**: Build automation
- **Docker**: For database and Temporal services
- **Deno 2.0+**: Task automation

### Local Development Setup

1. **Clone and Setup**
   ```bash
   git clone <repository-url>
   cd simple-temporal-demo
   deno task init
   ```

2. **Start Infrastructure**
   ```bash
   deno task up  # Starts MySQL + Temporal
   ```

3. **Run Development Server**
   ```bash
   deno task dev  # Starts Quarkus in dev mode
   ```

4. **Run Tests**
   ```bash
   deno task test                    # All tests
   deno task test --unit            # Unit tests only  
   deno task test --integration     # Integration tests only
   deno task test --coverage        # With coverage reports
   ```

### Available Tasks

| Command | Description |
|---------|-------------|
| `deno task up` | Start all infrastructure services |
| `deno task down` | Stop all services |
| `deno task dev` | Start development server with hot reload |
| `deno task test` | Run comprehensive test suite |
| `deno task build` | Build application for production |
| `deno task status` | Check system health |
| `deno task clean` | Clean build artifacts |

### Database Schema

The system uses a single `content` table with the following key fields:

- `id`: Primary key (auto-increment)
- `title`: Content title (indexed)
- `content`: Main content body (full-text indexed)
- `author_id`: Content author (indexed)
- `status`: Workflow status (indexed)
- `tags`: JSON array of tags
- `temporal_workflow_id`: Links to Temporal execution
- `created_date` / `updated_date`: Timestamps

## Testing

### Test Strategy

The project implements a comprehensive 5-category integration testing strategy:

- **Unit Tests**: Fast tests using TestWorkflowEnvironment with mocked activities
- **Temporal Service Integration**: Real Temporal service testing with workflow execution, signals, and queries
- **Activity Failure Testing**: Comprehensive failure scenario testing with retry behavior and error handling  
- **Performance Testing**: Load testing with 20+ concurrent workflows, memory monitoring, and latency analysis
- **Resilience Testing**: Worker restart simulation, high failure rates, timeout handling, and resource exhaustion
- **REST API Integration**: End-to-end API testing with complete workflow integration

### Running Tests

```bash
# Run all tests with Deno automation
deno task test                    # All tests
deno task test --unit            # Unit tests only
deno task test --integration     # All integration tests
deno task test --coverage        # With coverage reports

# Run specific integration test categories
mvn test -Dtest="TemporalServiceIntegrationTest"    # Real Temporal integration
mvn test -Dtest="ActivityFailureIntegrationTest"    # Failure scenarios
mvn test -Dtest="PerformanceIntegrationTest"        # Performance/load testing
mvn test -Dtest="ResilienceIntegrationTest"         # Resilience scenarios
mvn test -Dtest="RestApiIntegrationTest"            # API integration
```

### Test Infrastructure Requirements

Integration tests require running services:
```bash
deno task up     # Start MySQL + Temporal services + create namespace
```

The `up` command now handles everything automatically:
- Starts MySQL and Temporal services
- Creates the `content-approval` namespace
- Compiles the application
- Shows test-ready status

### Performance Benchmarks

- **Throughput**: > 0.1 workflows/second under load
- **Latency**: < 30 seconds average, < 60 seconds P95  
- **Memory**: < 10MB per workflow
- **Concurrency**: Support 20+ concurrent workflows

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `QUARKUS_DATASOURCE_JDBC_URL` | Database connection URL | `jdbc:mysql://localhost:3306/content_db` | No |
| `QUARKUS_DATASOURCE_USERNAME` | Database username | `content_user` | No |
| `QUARKUS_DATASOURCE_PASSWORD` | Database password | `content_pass` | No |
| `QUARKUS_TEMPORAL_SERVICE_URL` | Temporal service URL | `localhost:7233` | No |
| `QUARKUS_HTTP_PORT` | Application port | `8088` | No |

### Application Properties

Key configuration options in `application.properties`:

```properties
# Database
quarkus.datasource.db-kind=mysql
quarkus.flyway.migrate-at-start=true

# Temporal
quarkus.temporal.namespace=content-approval
quarkus.temporal.start-workers=true

# API
quarkus.smallrye-openapi.info-title=Content Approval API
```

## Deployment

### Docker Build

```bash
# Build native image (production)
deno task build --native

# Build JVM image (development)
deno task build
```

### Production Deployment

1. **Infrastructure**: Deploy MySQL and Temporal services
2. **Application**: Deploy the containerized Quarkus application
3. **Health Checks**: Monitor `/q/health` endpoint
4. **Metrics**: Access metrics via `/q/metrics` (if enabled)

## Monitoring

### Health Checks

The application provides several health check endpoints:

- `/q/health` - Overall health status
- `/q/health/live` - Liveness probe
- `/q/health/ready` - Readiness probe

### Logging

Structured logging is configured with:

- Application logs: `DEBUG` level
- Framework logs: `INFO` level
- Temporal logs: `INFO` level

## Contributing

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** changes: `git commit -m 'feat: add amazing feature'`
4. **Push** to branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Code Standards

- **Java**: Follow Google Java Style Guide
- **Testing**: Maintain >90% code coverage
- **Commits**: Use Conventional Commits format
- **Documentation**: Update relevant docs with changes

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Links

- **Quarkus**: https://quarkus.io/
- **Temporal**: https://temporal.io/
- **jOOQ**: https://www.jooq.org/
- **TestContainers**: https://www.testcontainers.org/