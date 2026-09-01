# Elicitation Prompts

Purpose
- Elicitation prompts guide users through structured, multi-step conversations to clarify goals, constraints, and acceptance criteria.
- They produce intermediate summaries and next-step suggestions that agents can use to invoke tools (e.g., create a Jira issue, create a Confluence page, or run a web search).

How it works
- Elicitation is exposed as an MCP prompt type (via `@McpPrompt`) and discovered like other prompts.
- The server injects relevant tools (including Web Search) so the agent can enrich missing context during the flow.
- Multi-turn support: the client/agent may re-invoke the prompt with updated `userMessage` or accumulated notes to progress the conversation.

Parameters
- `userMessage` (string, required): the user’s current input or answers.
- `agentName` (string, optional): agent persona or name used by the prompt to tailor responses.

Outputs
- A structured response containing:
  - A concise summary of gathered requirements
  - Outstanding questions or missing information
  - Recommended next actions (e.g., call `web_search_advanced` or `create_jira_issue`)

Workflow Example
1) Invoke elicitation prompt to refine requirements
```
{
  "jsonrpc": "2.0",
  "id": "10",
  "method": "prompts/execute",
  "params": {
    "name": "elicitation",
    "arguments": { "userMessage": "We need an internal status dashboard for our microservices" }
  }
}
```

2) Use recommended tools (e.g., web search or content extraction) during elicitation
```
{
  "jsonrpc": "2.0",
  "id": "11",
  "method": "tools/call",
  "params": {
    "name": "web_search_advanced",
    "arguments": { "query": "internal developer portal status dashboard best practices", "timeRange": "m" }
  }
}
```

3) After requirements are sufficiently detailed, invoke a creation tool (e.g., Jira)
```
{
  "jsonrpc": "2.0",
  "id": "12",
  "method": "tools/call",
  "params": {
    "name": "create_jira_issue",
    "arguments": {
      "summary": "Internal status dashboard for microservices",
      "description": "Build a dashboard aggregating service health and deployments",
      "acceptanceCriteria": [
        "Display per-service status and version",
        "Show latest deployment date per service"
      ]
    }
  }
}
```

Best Practices
- Start with elicitation when the request is unclear or new.
- Keep `userMessage` updates concise; include only new answers or decisions per turn.
- Use Web Search tools sparingly during elicitation to validate assumptions or gather references.

Interactive confirmations (MCP elicitation protocol)
- Separate from the prompt above: destructive tools pause mid-call and ask the user to confirm via `McpConfirmations.confirm(...)`, which issues an MCP `elicitation/create` request over the open Streamable HTTP session and blocks until the user answers.
- Callers branch on `ElicitResult.Action` (`ACCEPT` / `DECLINE` / `CANCEL`). Call sites: `JiraIssueTools.deleteJiraIssue`, and the pagination and bulk-transition paths in `JiraAgileService`.
- The confirmation carries a `chatId` in the request `_meta` so the answer can be correlated back to the originating conversation on the client side.

Request timeout — why this matters
- `spring.ai.mcp.server.request-timeout` (in `application.properties`, currently `600s`) bounds every **server-initiated** request, elicitation included. It does not affect tool execution or inbound client requests.
- Spring AI defaults this to **20 seconds**. That default is far too short for a prompt a human has to read and answer, and the failure mode is not a clean timeout:
  1. The timeout fires and the MCP session discards the pending response sink for that request id.
  2. The user answers a moment later; the client POSTs the JSON-RPC response to `/mcp`.
  3. The server no longer recognises the request id and answers **HTTP 500** (`Unexpected response for unknown id …`).
  4. The client has already resolved its own future, so the user sees the interaction as complete while the server never applied the answer.
- Symptom to look for on the client side: `McpClientSession` logging `Failed to send response to the server` with a 500 from `POST /mcp`, milliseconds after the elicitation resolved.
- Keep this setting in `application.properties` rather than a single profile — configuring it only in `local` is what let production run on the 20 second default.
- A confirmation that fails for any reason (timeout, or a client that does not advertise elicitation support) is logged with its `chatId` and rethrown as an `McpToolFailureException` naming the prompt, so the calling model gets an actionable message instead of a bare reactor timeout.

See also
- Prompts: ./prompts.md
- Web Search: ./web-search.md