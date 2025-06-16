# Simple Temporal Demo

Content approval system demonstrating Temporal workflow orchestration with Quarkus, MySQL, and jOOQ.

## Quick Start

```bash
deno task up && deno task dev
```

## Key Commands

- `deno task status` - Check system health
- `deno task up` - Start infrastructure (MySQL, Temporal)
- `deno task dev` - Start development server
- `deno task test` - Run tests (unit & integration)
- `deno task build` - Build application
- `deno task down` - Stop all services

## Architecture

- **Backend**: Quarkus 3.23.3 with Temporal workflows
- **Database**: MySQL 8.0 with TestContainers & jOOQ code generation
- **Automation**: Deno TypeScript scripts for cross-platform development

## Development URLs

- Application: http://localhost:8088
- Temporal UI: http://localhost:8081  
- Dev UI: http://localhost:8088/q/dev/

## Prerequisites

- Java 21+, Maven 3.9+, Docker Desktop, Deno 2.0+