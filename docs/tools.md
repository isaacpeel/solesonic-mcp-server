# Tools

Overview
- Tools are exposed via the MCP protocol and secured with method-level authorization.
- Names and expected parameters are discoverable via MCP tool listing after `initialize`.
- Tools may also be referenced from server-side prompts. See Prompts: ./prompts.md

Categories

- Jira Tools
  - create_jira_issue
    - Description: Guided workflow that creates a Jira issue from a plain-language request. Generates a summary, description, and acceptance criteria; resolves the assignee; then submits to Jira.
    - Auth: ROLE_MCP-JIRA-CREATE
    - Input: { "userMessage": "<string>" }
    - Output: Formatted Markdown block with the new issue key, link, summary, description, acceptance criteria, and assignee
  - get_jira_issue
    - Description: Retrieves a Jira issue by its key or numeric ID.
    - Auth: ROLE_MCP-JIRA-GET
    - Input: { "issueId": "<string>" }
    - Output: JiraIssue object
  - delete_jira_issue
    - Description: Deletes a Jira issue by its key or ID. Uses MCP elicitation to ask the user to confirm before the deletion is carried out.
    - Auth: ROLE_MCP-JIRA-DELETE
    - Input: { "keyOrIssueId": "<string>" }
    - Output: Confirmation, decline, or cancellation message

- Jira Agile Tools
  - agile-workflow
    - Description: Guided workflow for interacting with agile boards. Interprets a natural language question, identifies the relevant board, and returns board or issue data.
    - Auth: ROLE_MCP-JIRA-AGILE-LIST
    - Input: { "userMessage": "<string>" }
    - Output: Board or issue data as text

- Confluence Tools
  - create_confluence_page
    - Description: Creates a new Confluence page with a specified title and content.
    - Auth: controlled by Atlassian Token Broker scopes; no additional role required at the tool level
    - Input/Output: see feature-specific docs when enabled

- Web Search Tools
  - web_search
    - Description: General web search
    - Auth: ROLE_MCP-WEB-SEARCH
    - Input: { "query": "<string>", "maxResults": <int, optional> }
    - Output: WebSearchResponse
  - web_search_advanced
    - Description: Advanced search with domain and time filters
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

- Date and Time Tools
  - get_current_date
    - Description: Returns the current date in ISO format (YYYY-MM-DD). Defaults to UTC if no timezone is provided.
    - Auth: ROLE_MCP-TIME
    - Input: { "timezone": "<string, optional>" } (e.g., "America/New_York", "Europe/London")
    - Output: { "date": "<string>", "timezone": "<string>" }
  - get_current_time
    - Description: Returns the current time in ISO format (HH:mm:ss.SSS). Defaults to UTC if no timezone is provided.
    - Auth: ROLE_MCP-TIME
    - Input: { "timezone": "<string, optional>" }
    - Output: { "time": "<string>", "timezone": "<string>" }
  - get_current_date_time
    - Description: Returns the current date and time in ISO format (YYYY-MM-DDTHH:mm:ss.SSS). Defaults to UTC if no timezone is provided.
    - Auth: ROLE_MCP-TIME
    - Input: { "timezone": "<string, optional>" }
    - Output: { "dateTime": "<string>", "timezone": "<string>" }

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
- Authorization: Ensure callers have the required ROLE_ authorities:
  - Jira: ROLE_MCP-JIRA-CREATE, ROLE_MCP-JIRA-GET, ROLE_MCP-JIRA-DELETE
  - Agile: ROLE_MCP-JIRA-AGILE-LIST
  - Web Search: ROLE_MCP-WEB-SEARCH
  - Date/Time: ROLE_MCP-TIME
  - Weather (demo): ROLE_MCP-GET-WEATHER
- Error handling: Jira-related errors surface as descriptive messages; verify role membership and Token Broker configuration if failures persist

See also
- Web Search: ./web-search.md
- Prompts: ./prompts.md
