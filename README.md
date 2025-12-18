# Solesonic MCP Server

> A Spring Boot HTTP MCP (Model Context Protocol) server powered by Spring AI. Secured as an OAuth2 Resource Server (JWT) with group- and scope-based authorization, built-in Jira tooling, and an external Atlassian Token Broker integration.

[![Java](https://img.shields.io/badge/Java-24-blue.svg)](https://www.oracle.com/java/technologies/downloads/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](docs/license.md)

## Features

- HTTP MCP Endpoint — JSON-RPC over HTTP at `POST /mcp`
- Built‑in Jira tools — create issues, assign, and look up assignees; note: includes a simple example tool `weather_lookup`
- OAuth2 Resource Server (JWT) — JWT validation; group claims mapped to authorities
- MCP Authorization & Dynamic Client Registration — see [Authorization](docs/authorization.md)
- Group & Role Authorization — `groups` → `GROUP_<name>` `roles` → `ROLE_<role>`
- ⚡ Atlassian Token Broker — Client-credentials integration for short-lived Atlassian access tokens
- 🛡️ Production SSL Ready — PKCS12 keystore via `ssl` profile; TLS 1.2/1.3
- Web Search Tools — General, advanced, and news search via Tavily; content extraction
- MCP Prompts — Server-exposed prompts for structured workflows and agent guidance
- Elicitation Prompts — Guided workflows for structured user input and problem refinement

## Quick Start

### Prerequisites
- Java 25+
- Maven 3.9+
- Docker (optional, for production-like run)

### 1) Configure Environment
> Note: OS environment variables take precedence over `.env`.


```bash
# JWT verification (use one of the following depending on your IdP)
JWK_SET_URI=https://your-issuer/.well-known/jwks.json
# or
ISSUER_URI=https://your-issuer

# Jira tooling
JIRA_URL_TEMPLATE=https://your-domain.atlassian.net/browse/{key}
ATLASSIAN_TOKEN_BROKER_URL=https://your-token-broker.example.com/broker/atlassian/token
ATLASSIAN_TOKEN_BROKER_ISSUER_URI=https://your-authz-server/oauth2/token
ATLASSIAN_TOKEN_BROKER_CLIENT_ID=your-client-id
ATLASSIAN_TOKEN_BROKER_CLIENT_SECRET=your-client-secret
JIRA_CLOUD_ID_PATH=/path/to/your/cloud-id

```

> Note: OS environment variables take precedence over `.env`.

### 2) Build and Run (Local)

```bash
# Build
./mvnw clean verify

# Run (default profiles)
./mvnw spring-boot:run
```

- Base URL: https://localhost:9443 (when the `ssl` profile is active); otherwise http://localhost:9443
- MCP endpoint: POST /mcp

> Tip: To enable HTTPS locally, run with profiles `prod,ssl`:
> ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod,ssl

### 3) Verify Setup
Send an MCP initialize request (replace placeholders):

```bash
curl -k \
  -H "Authorization: Bearer <YOUR_JWT_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -X POST https://localhost:9443/mcp \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {"protocolVersion": "2024-11-05", "client": {"name": "curl", "version": "1.0"}}
  }'
```

> For a richer client experience, see [Clients](docs/clients.md) for MCP Inspector and Claude Desktop examples.

## MCP Server & Token Broker

- The server exposes tools via the MCP protocol. Tool invocation is authorized using JWT scopes and/or group authorities.
- Jira tools are first-class features. They rely on an external Atlassian Token Broker to mint short-lived access tokens from securely stored refresh tokens.

See:
- [Endpoints](docs/endpoints.md)
- [Tools](docs/tools.md)
- [Security](docs/security.md)
- [Configuration](docs/configuration.md)
- [Prompts](docs/prompts.md)
- [Web Search](docs/web-search.md)

## Documentation

- Start here: [Documentation](docs/documentation.md)
- Deep dives: [Configuration](docs/configuration.md), [Security](docs/security.md), [Endpoints](docs/endpoints.md), [Tools](docs/tools.md), [Prompts](docs/prompts.md), [Web Search](docs/web-search.md), [Deployment](docs/deployment.md), [Troubleshooting](docs/troubleshooting.md)

---

Ready to build with MCP? Jump into the [Quickstart](docs/quickstart.md).