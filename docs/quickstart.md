# Quickstart

Prerequisites
- JDK 24+
- Maven 3.9+
- Docker (optional)
- Node.js 18+ (optional, for MCP Inspector)

Local run (no Docker)
- Build: ./mvnw clean verify
- Start:
  - Default: java -jar target/solesonic-mcp-server-0.0.1.jar
  - With profiles (enable HTTPS): java -Dspring.profiles.active=prod,ssl -jar target/solesonic-mcp-server-0.0.1.jar
- Defaults:
  - Base URL: https://localhost:9443
  - MCP endpoint: POST /mcp

Docker Compose
- Build and run:
  - docker compose -f docker/docker-compose.yml up --build
- Environment
  - docker/docker-compose.yml reads .env at the project root
  - Uses profiles prod,ssl and mounts a PKCS12 keystore via volume
- Stop and remove containers:
  - docker compose -f docker/docker-compose.yml down

Verify
- Health (if exposed via management endpoints in your environment):
  - curl -ik https://localhost:9443/actuator/health
- MCP initialize with Authorization header (example using MCP Inspector):
  - npx @modelcontextprotocol/inspector --server-url https://localhost:9443/mcp --header "Authorization: Bearer <YOUR_JWT>"

Notes
- This server is an OAuth2 Resource Server; you must supply a valid JWT via Authorization: Bearer.
- .env is loaded automatically (overridden by OS environment variables). See Configuration for details.
- HTTPS requires running with the `ssl` profile (enabled in Docker Compose by default).

See also
- Configuration: ./configuration.md
- Security: ./security.md