# Troubleshooting

Authentication/Authorization
- 401 Unauthorized
  - Missing/invalid/expired JWT
  - Issuer/JWKS mismatch (check ISSUER_URI or JWK_SET_URI)
  - Clock skew issues
- 403 Forbidden
  - Token valid but missing required authority (e.g., ROLE_MCP-GET-WEATHER, ROLE_MCP-CREATE-JIRA, ROLE_MCP-WEB-SEARCH)

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

Web Search Issues
- 403 Forbidden
  - Ensure your token includes ROLE_MCP-WEB-SEARCH
- Timeouts or 5xx from Tavily
  - Retry with exponential backoff; reduce maxResults
  - Validate outbound network connectivity
- Extraction failures / partial results
  - Limit to max 5 URLs per call; inspect per-item status/message
- Rate limiting
  - Respect provider rate limits; implement client-side backoff

MCP Prompts Issues
- Prompt not discovered or listed
  - Re-run `prompts/list` after `initialize`; ensure the client supports MCP prompts
- Prompt invocation errors
  - Check `prompts/get` for required parameters; ensure JSON types match
- Tool injection failures
  - Prompts may reference tools you are not authorized to use; obtain the necessary ROLE_ authorities

Elicitation Issues
- Prompt loading errors
  - Verify prompt name and client support for prompts
- Template variable binding failures
  - Ensure required parameters (e.g., userMessage) are provided and correctly typed

Atlassian Token Broker
- 401/403 from broker: check client credentials, scopes, and token-uri
- 5xx from broker: inspect broker logs and retry with backoff
- Missing accessToken in response: validate TokenExchange payload (subject_token, audience)

Diagnostics
- Increase logging to DEBUG for com.solesonic packages
- Examine server logs for authenticationEntryPoint and accessDeniedHandler warnings