# Tools

Overview
- Tools are exposed via the MCP protocol and secured with method-level authorization.
- Names and expected parameters are discoverable via MCP tool listing after `initialize`.
- Tools may also be referenced from server-side prompts. See Prompts: ./prompts.md

Categories

- Jira Tools
  - assignee_id_lookup
    - Description: Look up a Jira accountId for a given assignee string
    - Auth: ROLE_MCP-CREATE-JIRA
    - Input: "<string>" (name or email)
    - Output: accountId string
  - create_jira_issue
    - Description: Creates a Jira issue. Use responsibly and avoid duplicate submissions; if an assignee is needed, resolve via assignee_id_lookup first.
    - Auth: ROLE_MCP-CREATE-JIRA
    - Input (CreateJiraRequest):
      - summary: string
      - description: string
      - acceptanceCriteria: array of strings
      - assigneeId: string (Jira accountId)
    - Output (CreateJiraResponse): { "issueId": "...", "issueUri": "..." }

- Confluence Tools (scaffolding)
  - create_confluence_page
    - Description: Create a documentation page from structured content
    - Auth: ROLE_MCP-CREATE-CONFLUENCE (subject to change if feature flag disabled)
    - Input/Output: see feature-specific docs when enabled

- Web Search Tools
  - web_search
    - Description: General web search
    - Auth: ROLE_MCP-WEB-SEARCH
    - Input: { "query": "<string>", "maxResults": <int, optional> }
    - Output: WebSearchResponse
  - web_search_advanced
    - Description: Advanced search with domain/time filters
    - Auth: ROLE_MCP-WEB-SEARCH
    - Input: { "query": "<string>", "includeDomains": ["<string>"], "excludeDomains": ["<string>"], "timeRange": "<string>", "maxResults": <int> }
    - Output: WebSearchResponse
  - web_search_news
    - Description: News-focused search optimized for freshness
    - Auth: ROLE_MCP-WEB-SEARCH
    - Input: { "query": "<string>", "timeRange": "<string>", "maxResults": <int> }
    - Output: WebSearchResponse
  - web_extract_content
    - Description: Extract full content from one or more URLs (max 5)
    - Auth: ROLE_MCP-WEB-SEARCH
    - Input: { "urls": ["<string>"] }
    - Output: WebExtractResponse
  - Details: See Web Search docs: ./web-search.md

- Utility Tools
  - weather_lookup
    - Description: Returns the weather in the given city (demo tool)
    - Auth: ROLE_MCP-GET-WEATHER
    - Input: { "city": "<string>" }
    - Output: string description

Atlassian Token Broker exchange (used by Jira tools)
- Purpose: Obtain short-lived Atlassian access tokens without exposing refresh tokens to this server
- Flow:
  1) This server uses OAuth2 client credentials (registration id: atlassian-token-broker) to authenticate to an external broker at atlassian.token.broker.uri
  2) For a given end-user (subject), it posts a TokenExchange payload: { "subject_token": "<UUID>", "audience": "atlassian" }
  3) The broker returns TokenResponse: { "accessToken": "...", "expiresInSeconds": 3600, "issuedAt": "<ISO8601>", "userId": "<UUID>", "siteId": "<optional>" }
  4) The accessToken is used for downstream Atlassian API calls (e.g., Jira issue creation, user search)
- Configuration:
  - atlassian.token.broker.uri
  - spring.security.oauth2.client.provider.atlassian-token-broker.token-uri
  - spring.security.oauth2.client.registration.atlassian-token-broker.*

Operational guidance
- Idempotency: The create_jira_issue tool should not be called repeatedly for the same request; consider upstream guards to prevent duplicates
- Authorization: Ensure callers have required ROLE_ authorities (e.g., ROLE_MCP-CREATE-JIRA, ROLE_MCP-WEB-SEARCH, ROLE_MCP-GET-WEATHER)
- Error handling: Jira-related errors surface as descriptive messages; verify role membership and Token Broker configuration if failures persist
 
See also
- Web Search: ./web-search.md
- Prompts: ./prompts.md