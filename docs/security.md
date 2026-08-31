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
  - Web Search tools require ROLE_MCP-WEB-SEARCH
  - Image generation requires ROLE_MCP-GENERATE-IMAGE
  - Gmail tools require ROLE_MCP-GMAIL-LIST, which covers get_gmail_message_body and therefore full message bodies
- There is no hard-coded required scope for /mcp in this repository; use group/role/scope-based method constraints to control access to individual tools.

Prompts
- Prompt discovery and execution are available to authenticated users; prompts themselves do not require additional authorities beyond standard MCP access.
- Prompts may reference tools that have their own authorization. Agents should handle 403 errors when following prompt guidance to call tools.

Token acquisition (example - placeholder)
- Using a client credentials grant (your IdP will differ):
  - export CLIENT_ID=<client-id>
  - export CLIENT_SECRET=<client-secret>
  - export TOKEN_URL=https://<issuer>/oauth2/token
  - curl -u "$CLIENT_ID:$CLIENT_SECRET" -d "grant_type=client_credentials&scope=<space-separated-scopes>" "$TOKEN_URL"

401 vs 403
- 401 Unauthorized: Missing/invalid token; issuer or JWKS misconfigured; expired token
- 403 Forbidden: Authenticated but lacking required authority (scope or group) for the tool

Abuse detection (fail2ban)
- Every 401/403 the security filter chain produces is also written as a fixed-grammar line to /var/log/solesonic-mcp-server/security.log (logger `security.audit`, additivity=false — this is the only writer of that file)
- Format: `<UTC timestamp> SECURITY event=<authn.failure|authz.denied> ip=<addr> method=<verb> path="<path>" status=<401|403> reason=<reason> route=<known|unknown>`
- `route=known` means the path falls under /a2a, /mcp, or /.well-known (see SecurityEventLogger.KNOWN_ROUTE_PREFIXES); anything else is `route=unknown` — a scanner, since the filter chain authenticates every request and there is no unauthenticated 404 to probe for
- fail2ban jails `solesonic-mcp-auth` (tolerant: 3 failures/hour on a known route) and `solesonic-mcp-probe` (one strike on an unknown route) parse this file; see /etc/fail2ban/filter.d/solesonic-mcp-{auth,probe}.conf and /etc/fail2ban/jail.d/solesonic-mcp.local on the deployment host
- This depends on `server.forward-headers-strategy=native` in the prod-nginx profile so `request.getRemoteAddr()` reflects the real client (via Tomcat's RemoteIpValve) rather than nginx's own address — and on nginx overwriting `X-Forwarded-For` rather than appending to it. Get either wrong and the log fills with the proxy's address instead of the attacker's.

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

External API considerations
- Web Search uses an external provider (Tavily). Consider rate limiting and retries with backoff for robustness.
- Image generation calls a self-hosted ComfyUI instance. ComfyUI has no authentication of its own, so `ROLE_MCP-GENERATE-IMAGE` protects this server's surface only — the ComfyUI origin must be restricted independently. See Deployment: ./deployment.md