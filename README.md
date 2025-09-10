# Solesonic MCP Server

> A Spring Boot HTTP MCP (Model Context Protocol) server powered by Spring AI. Secured as an OAuth2 Resource Server (JWT) with group- and scope-based authorization, optional Jira tooling, and an external Atlassian Token Broker integration.

[![Java](https://img.shields.io/badge/Java-24-blue.svg)](https://www.oracle.com/java/technologies/downloads/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Features

- 🔗 HTTP MCP Endpoint — JSON-RPC over HTTP at `POST /mcp`
- 🧰 Spring AI Tools — Example tool: `weather_lookup`; Jira tools available behind a feature flag
- 🔐 OAuth2 Resource Server (JWT) — Audience and scope validation; group claims mapped to authorities
- 👥 Group & Scope AuthZ — `cognito:groups` → `GROUP_<name>` authorities; scopes → `SCOPE_<scope>`
- ⚡ Atlassian Token Broker — Client-credentials integration for short-lived Atlassian access tokens
- 🛡️ Production SSL Ready — PKCS12 keystore via `ssl` profile; TLS 1.2/1.3

## Quick Start

### Prerequisites
- Java 24+
- Maven 3.9+
- Docker (optional, for production-like run)

### 1) Configure Environment
Create a `.env` file with minimal variables (examples):

```bash
# JWT verification (use one of the following depending on your IdP)
COGNITO_JWK_SET_URI=https://your-issuer/.well-known/jwks.json
# or
COGNITO_ISSUER_URI=https://your-domain.auth.your-region.amazoncognito.com

# Jira tooling (optional; only if enabling Jira tools)
JIRA_URL_TEMPLATE=https://your-domain.atlassian.net/browse/{key}
ATLASSIAN_TOKEN_BROKER_URL=https://your-token-broker.example.com/broker/atlassian/token
ATLASSIAN_TOKEN_BROKER_ISSUER_URI=https://your-authz-server/oauth2/token
ATLASSIAN_TOKEN_BROKER_CLIENT_ID=your-client-id
ATLASSIAN_TOKEN_BROKER_CLIENT_SECRET=your-client-secret
ATLASSIAN_TOKEN_BROKER_SCOPES=your.scope.here
JIRA_CLOUD_ID_PATH=/path/to/your/cloud-id

# Feature flags
MCP_JIRA_ENABLED=true
```

> Note: OS environment variables take precedence over `.env`.

### 2) Build and Run (Local)

```bash
# Build
./mvnw clean verify

# Run (default profiles)
./mvnw spring-boot:run
```

- Base URL: http://localhost:9443
- MCP endpoint: POST /mcp

### 3) Verify Setup
Send an MCP initialize request (replace placeholders):

```bash
curl -k \
  -H "Authorization: Bearer <YOUR_JWT_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:9443/mcp \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {"protocolVersion": "2024-11-05", "clientInfo": {"name": "curl", "version": "1.0"}}
  }'
```

> For a richer client experience, see [Clients](docs/clients.md) for MCP Inspector and Claude Desktop examples.

## MCP Server & Token Broker

- The server exposes tools via the MCP protocol. Tool invocation is authorized using JWT scopes and/or group authorities.
- Optional Jira tools can be enabled with `mcp.jira.enabled=true`. They rely on an external Atlassian Token Broker to mint short-lived access tokens from securely stored refresh tokens.

See:
- [Endpoints](docs/endpoints.md)
- [Tools](docs/tools.md)
- [Security](docs/security.md)
- [Configuration](docs/configuration.md)

## Documentation

- Start here: [docs/README.md](docs/README.md)
- Deep dives: [Configuration](docs/configuration.md), [Security](docs/security.md), [Endpoints](docs/endpoints.md), [Tools](docs/tools.md), [Deployment](docs/deployment.md), [Troubleshooting](docs/troubleshooting.md)

---

Ready to build with MCP? Jump into the [Quickstart](docs/quickstart.md).