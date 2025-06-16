# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Essential Commands

### Development Workflow
```bash
# Quick start - infrastructure + development server
deno task up && deno task dev

# Check system health (Docker services, application endpoints)
deno task status

# Run tests with TestContainers
deno task test --integration    # MySQL TestContainers tests
deno task test --unit          # Fast H2 tests (currently all tests use full Quarkus context)

# Build with optional flags
deno task build                # Standard JVM build
deno task build --native       # GraalVM native executable (slow)
deno task build --skip-tests   # Fast build without tests

# Infrastructure management
deno task up                   # Start MySQL + Temporal services, generate jOOQ classes
deno task down                 # Stop all Docker services
deno task clean                # Clean Maven artifacts and logs
```

### Manual Maven Operations
```bash
cd content-approval
mvn quarkus:dev                           # Start Quarkus dev mode directly
mvn test -Dtest=ContentCrudIntegrationTest  # Run single test
mvn clean compile                         # Trigger jOOQ code generation
```

## Architecture Overview

### Content Approval System Structure
**Quarkus application** (`content-approval/`) with **Deno automation scripts** at project root.

**Data Flow**: REST API → MySQL (via jOOQ) → Temporal Workflows → Status Updates

**Key Integration Points**:
- `temporal_workflow_id` column links database records to Temporal executions
- TestContainers for isolated integration testing with real MySQL instances
- jOOQ code generation from live MySQL schema during build process

### Database Architecture
- **Production**: MySQL 8.0 with application-specific schema
- **Testing**: MySQL TestContainers with "test" schema for isolation
- **Code Generation**: TestContainers jOOQ plugin generates type-safe query classes during compilation
- **Migration**: Flyway manages schema versions, must use MySQL-compatible SQL syntax

### Temporal Integration
- **Namespace**: `content-approval` 
- **Service**: Runs on port 7233 with MySQL backend
- **UI**: Available at http://localhost:8081
- **Workers**: Auto-started by Quarkus (`quarkus.temporal.start-workers=true`)

### Testing Strategy
**All tests currently use `@QuarkusTest`** (full application context), no pure unit tests exist.

**Integration Tests** use TestContainers with separate MySQL instances per test class:
- `ContentCrudIntegrationTest`: Database operations via jOOQ
- `DatabaseIntegrationTest`: Schema validation and CRUD
- `TemporalIntegrationTest`: Workflow client connectivity  
- `FlywayIntegrationTest`: Migration verification

## Development URLs

- **Application**: http://localhost:8088
- **Dev UI**: http://localhost:8088/q/dev/
- **Health Checks**: http://localhost:8088/q/health
- **Swagger**: http://localhost:8088/q/swagger-ui/
- **Temporal UI**: http://localhost:8081

## Critical Configuration Details

### jOOQ Code Generation
**Plugin**: `testcontainers-jooq-codegen-maven-plugin` starts MySQL container, runs Flyway migrations, generates Java classes from live schema.

**Schema Mapping**: `inputSchema=test` (matches TestContainers schema) → `packageName=com.wcygan.contentapproval.generated`

**Generated Classes**: `CONTENT` table, `ContentRecord`, schema metadata at `target/generated-sources/jooq/`

### MySQL Syntax Requirements
Migration files must use MySQL syntax:
- `LONGTEXT` instead of `CLOB`
- `TEXT` columns cannot have default values
- Use `ON UPDATE CURRENT_TIMESTAMP` for auto-updated timestamps

### Automation Script Architecture  
**Cross-platform Deno TypeScript scripts** using Dax library for shell operations:
- All Maven operations use `.cwd("content-approval")` 
- Docker Compose files referenced as `content-approval/docker-compose.yml`
- Error handling with `(error as Error).message` pattern
- Health checks for Docker services and application endpoints

## Package Structure

```
simple-temporal-demo/
├── content-approval/          # Quarkus application
│   ├── src/main/java/         # Application code (minimal - needs workflow implementation)
│   ├── src/test/java/         # Integration tests with TestContainers
│   ├── src/main/resources/    # application.properties, migrations
│   ├── docker-compose.yml     # MySQL + Temporal infrastructure
│   └── pom.xml               # Dependencies, jOOQ plugin configuration
└── scripts/                  # Deno automation (build, test, dev, infrastructure)
```

**Current Implementation Status**: Infrastructure and testing framework complete, but missing core Temporal workflow implementations and REST endpoints for content management.