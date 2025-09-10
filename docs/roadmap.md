# Roadmap

Near-term
- Jira authentication improvements and real Jira client integration beyond scaffolding
- Per-tool authorization refinements and clearer error surfaces
- Idempotency keys for create operations (e.g., create_jira_issue)
- Enhanced observability: structured logs, request IDs, selective tool-level metrics

Mid-term
- Rate limiting and backoff strategies for external APIs
- Standardized tool schemas and validation helpers
- Expanded Atlassian support (Confluence, transitions, comments)
- Metrics/tracing via Micrometer/OTel with dashboards

Notes
- Roadmap items are subject to change based on usage and requirements