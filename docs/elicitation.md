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

See also
- Prompts: ./prompts.md
- Web Search: ./web-search.md