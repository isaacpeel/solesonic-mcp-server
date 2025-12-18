# Web Search Tools

Overview
- The server provides four Web Search tools via a Tavily integration. These tools are available directly via MCP and are automatically injected into all prompts (basic, elicitation, etc.).
- Authorization: callers must have `ROLE_MCP-WEB-SEARCH`.

Tools and Signatures

- web_search
  - Description: General-purpose web search returning relevant results.
  - Auth: ROLE_MCP-WEB-SEARCH
  - Input:
    - `query` (string, required)
    - `maxResults` (integer, optional; default 5; range 1–10)
  - Output: `WebSearchResponse`

- web_search_advanced
  - Description: Advanced search with optional domain- and time-based filtering.
  - Auth: ROLE_MCP-WEB-SEARCH
  - Input:
    - `query` (string, required)
    - `maxResults` (integer, optional; default 10; range 1–25)
    - `timeRange` (string, optional; e.g., `d`, `w`, `m`, `y`)
    - `includeDomains` (array<string>, optional)
    - `excludeDomains` (array<string>, optional)
  - Output: `WebSearchResponse`

- web_search_news
  - Description: News-focused search prioritized for freshness and publisher signals.
  - Auth: ROLE_MCP-WEB-SEARCH
  - Input:
    - `query` (string, required)
    - `maxResults` (integer, optional; default 10; range 1–20)
    - `timeRange` (string, optional; recommended: `d`, `w`)
  - Output: `WebSearchResponse`

- web_extract_content
  - Description: Fetch and extract the full content from specific URLs.
  - Auth: ROLE_MCP-WEB-SEARCH
  - Input:
    - `urls` (array<string>, required; max 5)
  - Output: `WebExtractResponse`

Response Types
- WebSearchResponse
  - `query` (string)
  - `results` (array)
    - `title` (string)
    - `url` (string)
    - `snippet` (string)
    - `publishedAt` (string, optional ISO8601)
    - `source` (string, optional)

- WebExtractResponse
  - `items` (array)
    - `url` (string)
    - `content` (string) — best-effort extracted text
    - `status` (string) — `ok` or error code
    - `message` (string, optional) — error details when status != ok

Authorization
- All web search tools require `ROLE_MCP-WEB-SEARCH`.
- Add this role to your JWT or group/role mapping in your IdP.

Limitations and Best Practices
- Max URLs for extraction: 5.
- Reasonable result counts:
  - `web_search`: up to 10.
  - `web_search_advanced`: up to 25.
  - `web_search_news`: up to 20.
- Prefer `web_search_advanced` for domain/time scoping; use `web_search` for quick queries.
- Consider calling `web_extract_content` on a small subset of high-value URLs to reduce latency and rate-limit impact.

Examples (MCP JSON-RPC)

- Basic search
```
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "web_search",
    "arguments": { "query": "spring boot mcp server" }
  }
}
```

- Advanced search with filters
```
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "web_search_advanced",
    "arguments": {
      "query": "oauth2 resource server jwt best practices",
      "includeDomains": ["spring.io", "auth0.com"],
      "timeRange": "m",
      "maxResults": 15
    }
  }
}
```

- News search
```
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "web_search_news",
    "arguments": { "query": "java 25 release notes", "timeRange": "w" }
  }
}
```

- Content extraction
```
{
  "jsonrpc": "2.0",
  "id": "4",
  "method": "tools/call",
  "params": {
    "name": "web_extract_content",
    "arguments": { "urls": ["https://spring.io/blog/...", "https://auth0.com/blog/..."] }
  }
}
```

Integration
- Backed by the Tavily Search API.
- Configure Tavily credentials and settings in application properties or `.env` (see Configuration).
- Web Search tools are included automatically in prompt context; prompts may direct agents to use them.

Error Handling and Common Issues
- 403 Forbidden: missing `ROLE_MCP-WEB-SEARCH`.
- Network timeouts or 5xx from upstream provider: retry with backoff; consider reducing `maxResults`.
- Extraction failures: partial results are returned with per-URL status and message.

See also
- Security: ./security.md
- Configuration: ./configuration.md
- Tools: ./tools.md
- Prompts: ./prompts.md