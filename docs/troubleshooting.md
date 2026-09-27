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

Gmail Issues
- "Your Google account isn't connected"
  - The token broker answered RECONNECT_REQUIRED: the user has never completed Google consent, or has revoked it. Fix it by running the consent flow in solesonic-llm-api (`GET /google/auth/uri`), not by changing anything here.
- 403 Forbidden from the token broker (not from the MCP call)
  - This server's service account is missing the `token-mint-gmail` role. Grant it to the client used by the `atlassian-token-broker` registration.
- 403 Forbidden on the tool call
  - Ensure the caller's token includes ROLE_MCP-GMAIL-LIST
- "No authentication found in the security context"
  - Gmail tools read the caller's user id from the JWT subject, so they cannot work with `solesonic.agent.security.enabled=false` (the `local` profile). Run with security enabled.
- Consent fails for some users
  - The Google OAuth consent screen for these Gmail scopes is in Testing mode; only listed test users can complete it until the app passes a CASA assessment.

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

Prompt Template Compilation
- `IllegalArgumentException: The template string is not valid.` in the server log
  - A `.st` prompt under `src/main/resources/prompt/**` contains a literal `{` or `}`. Spring AI's
    `StTemplateRenderer` uses those as its expression delimiters, so a JSON example inside a prompt
    is parsed as an expression and fails to compile — e.g.
    `'[' came as a complete surprise to me`.
  - Write the example as prose, or escape the braces. `PromptTemplateCompilationTest` compiles every
    prompt resource and will catch this before deployment.

Tool Errors Reaching the Caller
- A tool result of `Error invoking method: <tool>` followed by a bare `null`
  - Spring AI's MCP tool callback renders the caller-visible error from the message of the
    *deepest* cause in the chain. If that cause carries no message (StringTemplate's `STException`,
    a bare `NullPointerException`, `Objects.requireNonNull` without a message), the calling model
    is shown the literal text `null`, learns nothing, and retries the same failing call.
  - Fail with `ToolFailures.describe(operation, exception)`
    (`com.solesonic.mcp.exception`) instead of propagating the raw exception. It flattens the whole
    cause chain into one message and carries no cause of its own, so the deepest cause is always
    itself. Log the original with its stack trace first — the flattened message is for the caller,
    the stack trace is for you.

Atlassian Token Broker
- 401/403 from broker: check client credentials, scopes, and token-uri
- 5xx from broker: inspect broker logs and retry with backoff
- Missing accessToken in response: validate TokenExchange payload (subject_token, audience)

Diagnostics
- Increase logging to DEBUG for com.solesonic packages
- Examine server logs for authenticationEntryPoint and accessDeniedHandler warnings