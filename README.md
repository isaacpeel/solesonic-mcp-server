# solesonic-mcp-server

A Spring Boot application exposing an HTTP Model Context Protocol (MCP) server using Spring AI.

This README explains how to run the server and how to set up an MCP client to connect to it.

## Prerequisites
- JDK 24+
- Maven 3.9+
- AWS Cognito (or another OAuth2 provider that can issue JWTs with the expected audience and scope)

The application is configured to load environment variables from a local `.env` file at runtime using `spring-dotenv`.

## Configure environment
Create or edit the `.env` file at the project root (this file is gitignored):

```
COGNITO_ISSUER_URI=https://example.auth.<region>.amazoncognito.com
# Optionally override the JWK set URI if needed
# COGNITO_JWK_SET_URI=https://cognito-idp.<region>.amazonaws.com/<userPoolId>/.well-known/jwks.json
```

Relevant properties from `src/main/resources/application.properties`:
```
spring.application.name=solesonic-mcp-server
spring.ai.mcp.server.name=solesonic-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=sync
spring.ai.mcp.server.capabilities.tool=true

# OAuth2 Resource Server (AWS Cognito)
spring.security.oauth2.resourceserver.jwt.issuer-uri=${COGNITO_ISSUER_URI}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${COGNITO_JWK_SET_URI}
spring.security.oauth2.resourceserver.jwt.audiences=api://solesonic-mcp
```

Notes:
- OS environment variables override values from `.env`.
- No extra flags are needed; `.env` is loaded automatically at startup.

## Build and run
```
# Build
mvn package

# Run (adjust the jar name if needed)
java -jar target/solesonic-mcp-server-0.0.1-SNAPSHOT.jar
```

Default base URL: `http://localhost:8080`

## Authentication and authorization
This server is protected as an OAuth2 Resource Server (JWT) and requires a valid bearer token on all requests.

- Expected audience: `api://solesonic-mcp` (see `application.properties`)
- Required scope: `MCP_INVOKE`
  - Spring Security maps OAuth scopes to authorities with a `SCOPE_` prefix. The code checks for `SCOPE_MCP_INVOKE`.

Important: The current `SecurityConfig` secures all endpoints. If you expect `/actuator/health` to be public, adjust the security config accordingly. As-is, you must provide a valid token for everything.

## MCP endpoint
This project uses the Spring AI MCP Server (WebMVC) starter, which exposes an HTTP MCP endpoint under `/mcp`.

- Base MCP HTTP endpoint: `POST http://localhost:8080/mcp`
- Transport: HTTP (JSON-RPC over HTTP as defined by MCP HTTP transport)
- Tools: This sample registers a simple tool named `message_me` that echoes a message

## Obtaining a token from Cognito (client credentials example)
You need a token that includes the `MCP_INVOKE` scope and the audience `api://solesonic-mcp`.

```
# 1) Get a token using client credentials (values are examples; replace placeholders)
TOKEN=$(curl -s \
  -u <clientId>:<clientSecret> \
  -d "grant_type=client_credentials&scope=MCP_INVOKE" \
  https://<your-domain>.auth.<region>.amazoncognito.com/oauth2/token \
  | jq -r .access_token)

echo "$TOKEN" | head -c 20; echo

# 2) Use the token to call the MCP endpoint (the inspector/client will normally perform the handshake; this is a sanity check)
curl -i \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -X POST \
  http://localhost:8080/mcp \
  --data '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"capabilities":{}},"meta":{}}'
```

If you receive 401/403, see Troubleshooting below.

## Set up a client for this MCP server
Below are two easy options to connect a client to this MCP server.

### Option A: MCP Inspector (CLI)
The MCP Inspector is a handy tool for interactive testing.

Requirements: Node.js 18+

```
# Install and run via npx
npx @modelcontextprotocol/inspector \
  --server http \
  --url http://localhost:8080/mcp \
  --header "Authorization: Bearer $TOKEN"
```

This will perform the MCP handshake and let you introspect tools like `message_me`.

### Option B: Claude Desktop (HTTP MCP server)
Claude Desktop supports MCP servers. You can register an HTTP MCP server with headers.

1) Open the Claude Desktop MCP configuration file (varies by OS). On macOS, for example:
- `~/Library/Application Support/Claude/mcp.json`

2) Add an entry for this server. Example snippet:
```
{
  "mcpServers": {
    "solesonic-mcp-server": {
      "transport": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer ${SOLESONIC_MCP_TOKEN}"
      }
    }
  }
}
```

3) Set an environment variable with your token before launching Claude Desktop:
- macOS/Linux: `export SOLESONIC_MCP_TOKEN="$TOKEN"`
- Windows (PowerShell): `$env:SOLESONIC_MCP_TOKEN = "$TOKEN"`

4) Restart Claude Desktop. You should see the `solesonic-mcp-server` MCP provider available, with the `message_me` tool.

Notes:
- If Claude Desktop does not yet support HTTP MCP with headers in your version, you can alternatively run a small local proxy that injects the Authorization header, or use the MCP Inspector for testing.

## Using the sample tool
With a connected client, invoke the `message_me` tool by passing a message argument; it will echo it back.

## Troubleshooting
- 401 Unauthorized
  - Token missing, expired, or not issued by the configured Cognito `issuer-uri`.
  - JWKS URL not reachable/misconfigured.
- 403 Forbidden
  - Token is valid but missing the required `MCP_INVOKE` scope. Ensure your OAuth client is allowed to request that scope and that Cognito includes it in the access token.
- Audience mismatch
  - Ensure the token audience matches `api://solesonic-mcp` or adjust `application.properties` accordingly.
- Connectivity
  - Verify the app is listening on `http://localhost:8080` and that `/mcp` is reachable.

## Notes
- `.env` is intended for local development only. Real OS environment variables override values from `.env`.
- The `.env` file is gitignored to prevent committing secrets.

## Work in progress
For ongoing enhancements, current status, and the roadmap, see the living document:

- [IN_PROGRESS.md](IN_PROGRES.md)



## MCP Jira Tools (Phase 0 scaffolding)
This project includes initial scaffolding for Jira MCP tools as per the migration plan (Phase 0). The feature is disabled by default and does not affect existing behavior.

Feature flag:
- mcp.jira.enabled=false (default)
- Set to true to enable Jira-related bean wiring (still using a Noop client until later phases).

Configuration properties (placeholders; configure via environment or application.properties as needed):
- atlassian.client-id
- atlassian.client-secret (optional with PKCE)
- atlassian.redirect-uri (e.g., http://127.0.0.1:8765/oauth/callback/atlassian)
- atlassian.cloud-id
- atlassian.project-id
- atlassian.issue-type-id
- atlassian.scopes (space-separated, e.g., "read:jira-user read:jira-work write:jira-work offline_access")
- atlassian.jira-base-url (e.g., https://your-domain.atlassian.net)

Notes:
- Tokens are not yet persisted; an in-memory TokenStore is provided as a placeholder.
- No OAuth or HTTP calls are active in Phase 0; a NoopJiraClient is wired when the feature flag is true.
- Future phases will add OAuth2 PKCE, secure token storage, a real Jira client using RestClient, idempotency, metrics, and @Tool methods.
