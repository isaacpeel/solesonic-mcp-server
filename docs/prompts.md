# MCP Prompts

Overview
- The server exposes prompts over MCP using server-side annotations (e.g., `@McpPrompt`).
- Clients discover prompts via MCP prompt listing and invoke them to receive agent-ready instructions.
- Prompts dynamically inject current tool context so responses stay up to date as tools evolve.

Discovery and Invocation
- After `initialize`, clients can list prompts, inspect prompt details, and invoke a prompt to obtain instructions.
- Typical MCP methods (client dependent):
  - `prompts/list` — discover available prompts
  - `prompts/get` — fetch a specific prompt’s schema/parameters
  - `prompts/execute` — render a prompt by providing required parameters

Parameters
- Common parameters across prompts:
  - `userMessage` — primary user input or question
  - `agentName` — optional agent identity for tailored instructions
  - Additional prompt-specific parameters as listed below

Dynamic Tool Injection
- Each prompt includes the current tool catalog and relevant tool context when rendered.
- This makes prompts “self-updating” — no hard-coded lists in prompt text.
- Web Search tools are injected into all general prompts by default.

Available Prompts

- basic-prompt (General Assistant)
  - Title: General Assistant
  - Description: General-purpose prompt for brainstorming, analysis, and coding help.
  - When to use: Most questions that do not require a specialized workflow.
  - Parameters: { `userMessage`: string, `agentName`: string (optional) }
  - Injected tools: All general-purpose tools including Web Search tools and utility helpers.

- jira-agile-board-prompt (Jira Agile Board Analysis)
  - Title: Jira Agile Board Analysis
  - Description: Analyze sprint/board health, active issues, and throughput.
  - When to use: Team retrospectives, sprint planning, or operational reviews.
  - Parameters: { `userMessage`: string, `agentName`: string (optional) }
  - Injected tools: Jira Agile and Issue tools; Web Search tools (for context enrichment when needed).

- create-confluence-page-prompt (Create Confluence Page)
  - Title: Create Confluence Page
  - Description: Produces instructions to gather content and create documentation in Confluence.
  - When to use: Documentation creation from requirements, summaries, or architectural notes.
  - Parameters: { `userMessage`: string, `agentName`: string (optional) }
  - Injected tools: Confluence creation tools (if enabled); Web Search tools.

- create-jira-issue-prompt (Create Jira Issue)
  - Title: Create Jira Issue
  - Description: Structured issue creation with summary, description, acceptance criteria, and optional assignee.
  - When to use: Turning a set of requirements into a Jira issue.
  - Parameters: { `userMessage`: string, `agentName`: string (optional) }
  - Injected tools: `create_jira_issue`, `assignee_id_lookup`; Web Search tools.

- elicitation prompts (Requirements Elicitation)
  - Title: Elicitation (multi-turn)
  - Description: Guides users through structured information gathering to refine the problem statement and desired outcomes.
  - When to use: At the beginning of ambiguous or underspecified tasks; often precedes Jira issue creation.
  - Parameters: { `userMessage`: string, `agentName`: string (optional) }
  - Injected tools: Web Search tools and relevant domain tools depending on the target workflow.

Example: Discover and Execute a Prompt (HTTP JSON-RPC)
- List prompts
```
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json

{ "jsonrpc": "2.0", "id": "1", "method": "prompts/list", "params": {} }
```

- Get a prompt
```
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json

{ "jsonrpc": "2.0", "id": "2", "method": "prompts/get", "params": { "name": "basic-prompt" } }
```

- Execute a prompt
```
POST /mcp
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "prompts/execute",
  "params": {
    "name": "basic-prompt",
    "arguments": {
      "userMessage": "Give me a plan to migrate our monolith to microservices",
      "agentName": "PlatformAssistant"
    }
  }
}
```

Integration with LLM Agents
- Prompts return agent-ready instructions that reference current tools by name.
- Agents can follow-up by calling tools listed in the instructions.
- See also
  - Web Search tools: ./web-search.md
  - Examples: ./examples/prompts-examples.md
