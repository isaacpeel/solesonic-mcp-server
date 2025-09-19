# Architecture Plan: MCP Dynamic Client Registration (OAuth 2.1 + AWS Cognito)

This document is a complete architecture plan to implement Dynamic Client Registration (DCR) for this Spring Boot MCP server in alignment with the MCP Authorization specification (version 2025‑06‑18). It integrates OAuth 2.1 practices, RFC 7591/7592 (DCR), RFC 8414 (AS metadata), RFC 8707 (Resource Indicators), and RFC 9728 (Protected Resource Metadata). The Authorization Server is Amazon Cognito; DCR is provided by an AWS-hosted facade (API Gateway + Lambda) that translates RFC 7591/7592 requests to Cognito Admin API calls.

The MCP server remains a pure resource server. Clients dynamically discover authorization requirements, register themselves, obtain targeted tokens, and invoke protected endpoints with least-privilege scopes.

---

## 1. Objectives and Compliance

- MCP Authorization (2025‑06‑18)
    - Publish Protected Resource Metadata (PRM) at a well-known endpoint.
    - On unauthorized access, return 401 with a WWW-Authenticate header that directs clients to PRM.
    - Require resource-bound tokens; enforce audience/resource binding.
    - Support OAuth 2.1 flows with Authorization Server metadata discovery.

- OAuth and IETF RFCs
    - RFC 9728: Publish PRM for this resource.
    - RFC 8414: Clients discover AS metadata (Cognito OIDC discovery).
    - RFC 8707: Clients indicate the resource during token requests; server validates tokens are targeted to this resource.
    - RFC 7591/7592: Provide DCR endpoints for client create/read/update/delete.

- Constraints
    - Cognito does not expose RFC 7591/7592 endpoints; a DCR facade will implement these endpoints and call Cognito Admin APIs.
    - Cognito does not natively honor RFC 8707’s resource parameter; the architecture enforces resource targeting using scopes and an optional Pre Token Generation Lambda that injects a “resource” claim.

---

## 2. System Components

- MCP Resource Server (this Spring Boot application)
    - Validates JWTs (existing).
    - Publishes PRM (/.well-known/oauth-protected-resource).
    - Returns 401 + WWW-Authenticate when tokens are missing/invalid.
    - Enforces scopes per endpoint and validates resource targeting.
    - Optionally validates a custom “resource” JWT claim when strict mode is enabled.

- Authorization Server (AS)
    - Amazon Cognito User Pool (OIDC provider).
    - Hosts authorization and token endpoints.
    - Defines a Resource Server and scopes corresponding to this MCP API.

- DCR Facade (AWS)
    - API Gateway: Exposes RFC 7591/7592 endpoints:
        - POST /oauth2/register
        - GET /oauth2/register/{client_id}
        - PUT /oauth2/register/{client_id}
        - DELETE /oauth2/register/{client_id}
    - Lambda: Implements RFC 7591/7592 behavior and calls Cognito Admin APIs (Create/Describe/Update/Delete User Pool Client).
    - Security:
        - Initial Access Token (IAT) required for POST /oauth2/register.
        - Registration Access Token (RAT) required for GET/PUT/DELETE.

- Optional Cognito Pre Token Generation Lambda
    - Injects a custom “resource” claim into access tokens based on requested scopes or client attributes, enabling strict resource enforcement.

---

## 3. Resource Identification and Scopes

- Resource Identifier (PRM “resource” value)
    - A stable, URI-like identifier for this API, e.g., https://api.example.com/mcp.
    - Configurable via application properties.

- Scopes
    - Defined in Cognito Resource Server and enforced by the MCP server.
    - Example set:
        - api.mcp.read — read-only access
        - api.mcp.write — mutating access
        - api.mcp.tools — tool-specific access (adjust as needed)
    - Scopes are documented in PRM.

---

## 4. Discovery and Challenge Behavior

- Protected Resource Metadata (PRM) endpoint
    - Path: /.well-known/oauth-protected-resource
    - Returns JSON with at least:
        - resource: string (resource identifier)
        - authorization_servers: array of URLs to AS metadata (Cognito issuer’s OIDC discovery document)
        - scopes_supported: array of strings
        - token_types_supported: ["Bearer"]
        - token_endpoint_auth_methods_supported: at least ["client_secret_basic"] (include others if supported)
        - Optionally: a “registration_endpoint” pointing to the DCR facade (to improve developer experience)

- 401 Unauthorized with WWW-Authenticate
    - For requests without/invalid tokens, respond 401.
    - Include a WWW-Authenticate header that:
        - Indicates Bearer scheme.
        - Identifies the realm with the resource identifier.
        - Provides a link to PRM so clients can discover how to authenticate.
    - Example (illustrative):
        - WWW-Authenticate: Bearer realm="https://api.example.com/mcp", title="MCP Resource", link="https://api.example.com/.well-known/oauth-protected-resource"

---

## 5. Token Targeting and Validation (RFC 8707)

- Client behavior
    - When requesting tokens from the AS, clients include the intended resource (per RFC 8707) and request appropriate scopes for this API.
    - With Cognito, if the “resource” parameter is ignored, clients still request the API’s scopes; the “resource” claim can be injected server-side as described below.

- Server enforcement
    - Scope enforcement: Endpoints require SCOPE_api.mcp.read or SCOPE_api.mcp.write (etc.).
    - Resource enforcement:
        - Strict mode (recommended for production): Require a JWT claim (e.g., resource) to equal the configured resource identifier; reject otherwise.
        - Compatibility mode: Where claim injection is unavailable, enforce via scopes and known client assignments; upgrade to strict mode when possible.

- Cognito Pre Token Generation Lambda (optional)
    - Adds "resource": "https://api.example.com/mcp" to tokens based on requested scopes or client.
    - Enables strict, claim-based resource validation in the MCP server.

---

## 6. Dynamic Client Registration (RFC 7591/7592) via AWS Facade

- API Contracts (Facade)
    - POST /oauth2/register
        - Auth: IAT in Authorization header.
        - Request: RFC 7591 client metadata (e.g., client_name, redirect_uris, grant_types, scope, token_endpoint_auth_method).
        - Behavior: Maps metadata to Cognito CreateUserPoolClient; sets generateSecret=true for confidential clients; sets enableClientCredentials=true for M2M.
        - Response: client_id, client_secret (if applicable), client_id_issued_at, client_secret_expires_at (if applicable), registration_access_token (RAT), registration_client_uri, echo of final metadata.
    - GET /oauth2/register/{client_id}
        - Auth: RAT.
        - Behavior: DescribeUserPoolClient; return client metadata.
    - PUT /oauth2/register/{client_id}
        - Auth: RAT.
        - Behavior: UpdateUserPoolClient with provided metadata; return updated metadata.
    - DELETE /oauth2/register/{client_id}
        - Auth: RAT.
        - Behavior: DeleteUserPoolClient; return success status.

- Metadata Mapping (RFC 7591 → Cognito)
    - client_name → clientName
    - redirect_uris → callbackURLs
    - post_logout_redirect_uris → logoutURLs
    - grant_types → allowedOAuthFlows (authorization_code → "code"; client_credentials → "client_credentials"); set allowedOAuthFlowsUserPoolClient=true
    - scope → allowedOAuthScopes (must match Cognito resource server scopes)
    - token_endpoint_auth_method → generateSecret or advanced methods (Cognito generally uses client secrets; if private_key_jwt is required, manage keys out-of-band or via a proxy)

- Security Model
    - IAT:
        - Required for POST /oauth2/register.
        - Issuance options:
            - KMS-signed, short-lived JWTs minted by an internal admin service.
            - API keys with usage plans (simpler, less standard).
    - RAT:
        - Returned on successful registration, bound to client_id.
        - JWT signed with KMS, or a random token stored in DynamoDB with expiry.
        - Required for GET/PUT/DELETE.
        - Supports rotation and revocation.

- IAM and Access Control
    - Lambda execution role may perform:
        - cognito-idp:CreateUserPoolClient
        - cognito-idp:DescribeUserPoolClient
        - cognito-idp:UpdateUserPoolClient
        - cognito-idp:DeleteUserPoolClient
    - Restrict to the specific User Pool ARNs.

- Observability and Safety
    - Redact client_secret in logs.
    - CloudWatch metrics and alarms (error rates, throttles, authorization failures).
    - WAF and rate limits if publicly accessible.

---

## 7. Amazon Cognito Configuration

- User Pool
    - Create or select a User Pool for the environment.

- Resource Server and Scopes
    - Resource server identifier: e.g., api.mcp
    - Scopes: api.mcp.read, api.mcp.write, api.mcp.tools (as required).

- Pre Token Generation Lambda (optional but recommended)
    - Inject “resource” claim to tokens based on requested scopes or client attributes.
    - Aligns tokens with RFC 8707 targeting and MCP Authorization enforcement.

- App Clients
    - Dynamic clients are created/updated/deleted exclusively by the DCR facade.
    - Maintain a separate static app client for admin/automation if necessary (not exposed by DCR).

---

## 8. MCP Server Responsibilities and Changes

- Configuration
    - mcp.resource.id: the resource identifier (e.g., https://api.example.com/mcp).
    - mcp.oauth.authorizationServers: list of AS metadata URLs (Cognito OIDC discovery).
    - mcp.oauth.scopes: array of supported scopes.
    - mcp.oauth.registrationEndpoint (optional): URL to the DCR facade register endpoint.
    - mcp.auth.strictResourceBinding: boolean to enable/disable strict resource claim validation.

- Endpoints
    - GET /.well-known/oauth-protected-resource:
        - Return PRM JSON as specified above.
    - Optional GET /.well-known/mcp-oauth-metadata:
        - Non-standard helper that references PRM, AS metadata, and registration endpoint to improve developer experience.

- Security Filter Chain
    - 401 + WWW-Authenticate on missing/invalid tokens with link to PRM.
    - Scope enforcement per route or method.
    - Optional resource claim validator enabled via configuration.

- Token Validation
    - Continue using JWT validation against Cognito issuer and JWKS.
    - Additional checks for scopes and resource claim (if strict mode enabled).

---

## 9. End-to-End Client Flow

1) Client sends a request without a token → Server responds 401 with WWW-Authenticate linking to PRM.
2) Client fetches PRM (/.well-known/oauth-protected-resource) → learns:
    - Resource identifier.
    - AS discovery URL(s).
    - Supported scopes and auth methods.
3) Client fetches AS metadata (RFC 8414) from Cognito’s OIDC discovery endpoint.
4) Client performs Dynamic Client Registration:
    - POST to DCR facade /oauth2/register with IAT and client metadata.
    - Receives client_id, client_secret (if applicable), RAT, and registration_client_uri.
5) Client initiates OAuth 2.1 flow:
    - Interactive: Authorization Code + PKCE.
    - M2M: client_credentials.
    - Requests scopes for this API and includes the intended resource (RFC 8707).
6) Client receives token(s) from AS.
    - If Pre Token Generation Lambda is configured, token contains "resource" claim.
7) Client calls MCP endpoints with Authorization: Bearer <access_token>.
    - Server validates signature, issuer, scopes, and resource targeting (strict mode if enabled).
8) Client manages registration:
    - GET/PUT/DELETE via registration_client_uri using RAT as needed.

---

## 10. Error Handling and Interoperability

- Server returns 401 with proper WWW-Authenticate when missing/invalid token to guide clients to PRM.
- Server returns 403 when scopes are insufficient or resource binding is violated (strict mode).
- DCR facade returns RFC 7591/7592-consistent error JSON for invalid requests, invalid/expired IAT/RAT, or denied operations.
- Maintain backward compatibility by:
    - Allowing non-strict resource validation in lower environments.
    - Ensuring PRM and WWW-Authenticate are always available for discovery.

---

## 11. Security and Privacy

- No secrets (client_secret, RAT, IAT) are logged.
- IAT and RAT are short-lived; use KMS for signing.
- Enforce rate limiting, WAF, and monitoring on the DCR facade.
- Apply IAM least privilege for Lambda and KMS.
- Prefer client_secret_basic for initial rollout; evaluate private_key_jwt according to organizational policy.

---

## 12. Observability

- Structured logging for auth challenges, PRM requests, and authorization failures (without sensitive data).
- Metrics and alerts:
    - Unauthorized/Forbidden rates.
    - DCR facade error rates and throttling.
    - Token validation failures due to scope or resource mismatch.
- Tracing (if available) through API Gateway, Lambda, and the MCP server for end-to-end visibility.

---

## 13. Deployment and Migration Strategy

- Phase 1 — Server Discovery & Enforcement
    - Add PRM endpoint and 401 + WWW-Authenticate behavior.
    - Configure and enforce scopes.
    - Add optional resource claim validator (disabled in non-prod by default).

- Phase 2 — DCR Facade & Cognito
    - Deploy API Gateway + Lambda facade with IAT/RAT mechanisms and IAM roles.
    - Configure Cognito Resource Server and scopes.
    - Add Pre Token Generation Lambda if strict resource binding is required.

- Phase 3 — Validation & Enablement
    - Validate end-to-end client flows in staging: discovery → registration → authorization → invocation.
    - Enable strict resource binding in production once token claim flow is verified.

---

## 14. Acceptance Criteria

- PRM endpoint returns valid metadata with correct resource identifier, AS metadata URLs, scopes, and token auth methods.
- Unauthorized requests return 401 with WWW-Authenticate linking to PRM.
- Scope enforcement is effective; insufficient scopes yield 403.
- With strict mode enabled, tokens lacking the correct resource targeting are rejected with 403.
- DCR facade supports RFC 7591/7592 CRUD with IAT (POST) and RAT (GET/PUT/DELETE); created clients appear in Cognito with configured flows and scopes.
- A new MCP client can fully onboard: discover → register → obtain token → call protected endpoints → manage registration.