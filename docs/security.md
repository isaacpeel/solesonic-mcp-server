# Security

Overview
- This application is an OAuth2 Resource Server that validates JWTs
- Authentication and authorization are enforced for all HTTP requests
- Authorities are derived from:
  - scope claim → SCOPE_<scope>
  - groups claim → GROUP_<group>
  - roles claim → ROLE_<role>

Authentication
- Configure issuer or JWKS:
  - spring.security.oauth2.resourceserver.jwt.issuer-uri
  - or spring.security.oauth2.resourceserver.jwt.jwk-set-uri
- Supply the token via Authorization: Bearer <JWT>

Authorization
- Global rule: all requests require authentication
- Tool-level restrictions via @PreAuthorize
  - Weather tool requires ROLE_MCP-GET-WEATHER
  - Jira tools require ROLE_MCP-CREATE-JIRA
- There is no hard-coded required scope for /mcp in this repository; use group/role/scope-based method constraints to control access to individual tools.

Token acquisition (example - placeholder)
- Using a client credentials grant (your IdP will differ):
  - export CLIENT_ID=<client-id>
  - export CLIENT_SECRET=<client-secret>
  - export TOKEN_URL=https://<issuer>/oauth2/token
  - curl -u "$CLIENT_ID:$CLIENT_SECRET" -d "grant_type=client_credentials&scope=<space-separated-scopes>" "$TOKEN_URL"

401 vs 403
- 401 Unauthorized: Missing/invalid token; issuer or JWKS misconfigured; expired token
- 403 Forbidden: Authenticated but lacking required authority (scope or group) for the tool

Atlassian Token Broker (high-level)
- Jira tools rely on an external Token Broker for secure, short-lived Atlassian access tokens
- This server authenticates to the broker using OAuth2 client credentials with the registration id atlassian-token-broker
- For each Jira operation, the server posts a TokenExchange payload with a subject_token (UUID of the end-user) and an audience value of "atlassian" to atlassian.token.broker.uri
- The broker returns a TokenResponse containing an accessToken and metadata (expiresInSeconds, issuedAt, userId, optional siteId)
- The returned accessToken is then used for Atlassian API calls

Troubleshooting
- Verify issuer-uri/jwk-set-uri matches the token’s issuer
- Confirm your token has the required groups/scopes
- Check TLS/SSL settings when running with the ssl profile
- Inspect server logs for access denied (403) or unauthorized (401) reasons