# Web Search Examples

Scenario: Research an architectural topic, extract key references, and open a follow-up task

1) Advanced search constrained to trusted domains
```
{
  "jsonrpc": "2.0",
  "id": "201",
  "method": "tools/call",
  "params": {
    "name": "web_search_advanced",
    "arguments": {
      "query": "zero-downtime deploy spring boot kubernetes",
      "includeDomains": ["spring.io", "kubernetes.io"],
      "timeRange": "y",
      "maxResults": 12
    }
  }
}
```

2) Extract content from top results (limit to a few URLs)
```
{
  "jsonrpc": "2.0",
  "id": "202",
  "method": "tools/call",
  "params": {
    "name": "web_extract_content",
    "arguments": { "urls": ["https://spring.io/guides/...", "https://kubernetes.io/docs/..."] }
  }
}
```

3) Create a Jira issue using synthesized requirements
```
{
  "jsonrpc": "2.0",
  "id": "203",
  "method": "tools/call",
  "params": {
    "name": "create_jira_issue",
    "arguments": {
      "summary": "Implement zero-downtime deployments for API service",
      "description": "Adopt rolling updates and preStop hooks as per references.",
      "acceptanceCriteria": [
        "No downtime observed during deployments in staging",
        "Deployment docs updated with runbook"
      ]
    }
  }
}
```

Notes
- Ensure your token has `ROLE_MCP-WEB-SEARCH` to use the search/extraction tools.
- Keep extraction to max 5 URLs per call to avoid errors and reduce latency.