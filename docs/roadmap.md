# Roadmap

Near-term
- Jira authentication improvements and real Jira client integration beyond scaffolding
- Per-tool authorization refinements and clearer error surfaces
- Idempotency keys for create operations (e.g., create_jira_issue)
- Enhanced observability: structured logs, request IDs, selective tool-level metrics
- Web search result caching and deduplication
- Prompt versioning and change logs

Mid-term
- Rate limiting and backoff strategies for external APIs
- Standardized tool schemas and validation helpers
- Expanded Atlassian support (Confluence, transitions, comments)
- Metrics/tracing via Micrometer/OTel with dashboards
- Additional specialized prompts (e.g., incident analysis, release notes synthesis)

Completed (this release)
- Web Search tools (Tavily integration): web_search, web_search_advanced, web_search_news, web_extract_content
- MCP Prompts: server-exposed prompts with dynamic tool injection
- Elicitation: guided multi-turn workflows for requirement gathering

Notes
- Roadmap items are subject to change based on usage and requirements