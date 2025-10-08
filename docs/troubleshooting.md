# Troubleshooting

Authentication/Authorization
- 401 Unauthorized
  - Missing/invalid/expired JWT
  - Issuer/JWKS mismatch (check ISSUER_URI or JWK_SET_URI)
  - Clock skew issues
- 403 Forbidden
  - Token valid but missing required authority (GROUP_MCP-GET-WEATHER, GROUP_MCP-CREATE-JIRA)

Connectivity/ports
- Local/Docker default: 9443
- Verify port exposure in docker-compose and that no firewall is blocking

SSL/Keystore
- With ssl profile: ensure SSL_CERT_LOCATION and KEYSTORE_PASSWORD are set
- Check keystore type is PKCS12 and alias matches configuration
- For self-signed certs, clients must trust the CA or disable verification for testing

MCP handshake issues
- Ensure Authorization header is present
- Confirm correct /mcp URL and HTTPS scheme when ssl is enabled
- Validate JSON-RPC structure (jsonrpc, id, method, params)

Atlassian Token Broker
- 401/403 from broker: check client credentials, scopes, and token-uri
- 5xx from broker: inspect broker logs and retry with backoff
- Missing accessToken in response: validate TokenExchange payload (subject_token, audience)

Diagnostics
- Increase logging to DEBUG for com.solesonic packages
- Examine server logs for authenticationEntryPoint and accessDeniedHandler warnings