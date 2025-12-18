# Prompt Examples

Discover prompts
```
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json

{ "jsonrpc": "2.0", "id": "100", "method": "prompts/list", "params": {} }
```

Get a specific prompt
```
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json

{ "jsonrpc": "2.0", "id": "101", "method": "prompts/get", "params": { "name": "basic-prompt" } }
```

Invoke basic-prompt (General Assistant)
```
{
  "jsonrpc": "2.0",
  "id": "102",
  "method": "prompts/execute",
  "params": {
    "name": "basic-prompt",
    "arguments": { "userMessage": "List top strategies to reduce CI build time for a Maven project" }
  }
}
```

Invoke jira-agile-board-prompt (Board analysis)
```
{
  "jsonrpc": "2.0",
  "id": "103",
  "method": "prompts/execute",
  "params": {
    "name": "jira-agile-board-prompt",
    "arguments": { "userMessage": "Analyze sprint velocity and rollover across the last two sprints" }
  }
}
```

Invoke create-confluence-page-prompt (Documentation)
```
{
  "jsonrpc": "2.0",
  "id": "104",
  "method": "prompts/execute",
  "params": {
    "name": "create-confluence-page-prompt",
    "arguments": { "userMessage": "Draft a runbook for incident response for our API gateway" }
  }
}
```

Invoke create-jira-issue-prompt (Issue creation)
```
{
  "jsonrpc": "2.0",
  "id": "105",
  "method": "prompts/execute",
  "params": {
    "name": "create-jira-issue-prompt",
    "arguments": { "userMessage": "Create a feature request for SSO integration with acceptance criteria" }
  }
}
```

Invoke elicitation (Requirements gathering)
```
{
  "jsonrpc": "2.0",
  "id": "106",
  "method": "prompts/execute",
  "params": {
    "name": "elicitation",
    "arguments": { "userMessage": "We need to improve developer onboarding experience" }
  }
}
```

Following prompt guidance with tools
- Example: if a prompt recommends web search, call `web_search_advanced` next
```
{
  "jsonrpc": "2.0",
  "id": "107",
  "method": "tools/call",
  "params": {
    "name": "web_search_advanced",
    "arguments": { "query": "developer onboarding best practices", "timeRange": "m", "maxResults": 10 }
  }
}
```

Notes
- Prompts include dynamic tool context; available tools evolve with the server.
- Web Search tools are included in prompt context by default.