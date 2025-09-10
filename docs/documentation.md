# Solesonic MCP Server Documentation

A secure HTTP Model Context Protocol (MCP) server built with Spring Boot and Spring AI. Designed for platform teams, MCP developers, and security-conscious organizations that need an authenticated MCP endpoint with built-in Atlassian (Jira) tooling via a token-brokered flow.

Who this is for
- MCP developers integrating tools over HTTP JSON-RPC
- Platform/integration engineers deploying secured MCP services
- Security teams validating OAuth2/JWT, scopes/groups, and token broker flows

Table of Contents
- Overview and Architecture
- Quickstart
- Configuration
- Security
- Endpoints (MCP over HTTP)
- Tools
- Clients
- Deployment
- Observability
- Troubleshooting
- Roadmap
- FAQ
- Contributing
- License

Overview and Architecture
- Protocol: HTTP JSON-RPC MCP endpoint at POST /mcp
- Security: OAuth2 Resource Server (JWT)
  - Scope authorities are exposed as SCOPE_<scope>
  - Cognito groups are exposed as GROUP_<group>
- Atlassian integration: Jira tools call an external Atlassian Token Broker to obtain short-lived access tokens for Atlassian APIs
- Profiles and ports:
  - Default port: 9443 (HTTPS when ssl profile enabled)
  - Docker publishes 9443:9443 by default

High-level flow
1) Client obtains a JWT suitable for this resource server
2) Client connects to POST /mcp and performs the MCP initialize handshake with Authorization: Bearer <token>
3) Tools are discovered via MCP and invoked on demand
4) For Jira tools, the server uses client-credential auth to call a Token Broker, exchanging a subject user ID for an Atlassian access token used by backend Jira calls

See also
- Quickstart: ./quickstart.md
- Security: ./security.md
- Configuration: ./configuration.md
- Endpoints: ./endpoints.md
- Tools: ./tools.md
- Clients: ./clients.md
- Deployment: ./deployment.md
- Observability: ./observability.md
- Troubleshooting: ./troubleshooting.md
- Roadmap: ./roadmap.md
- FAQ: ./faq.md
- Contributing: ./contributing.md
- License: ./license.md