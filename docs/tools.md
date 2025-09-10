# Tools

Overview
- Tools are exposed via the MCP protocol and secured with method-level authorization
- Names and expected parameters are discoverable via MCP tool listing after initialize

Catalog
- weather_lookup
  - Description: Returns the weather in the given city (demo tool)
  - Auth: GROUP_MCP-GET-WEATHER
  - Input: { "city": "<string>" }
  - Output: string description
- assignee_id_lookup
  - Description: Look up a Jira accountId for a given assignee string
  - Auth: GROUP_MCP-CREATE-JIRA
  - Input: "<string>" (name or email)
  - Output: accountId string
- create_jira_issue
  - Description: Creates a Jira issue. Use responsibly and avoid duplicate submissions; if an assignee is needed, resolve via assignee_id_lookup first.
  - Auth: GROUP_MCP-CREATE-JIRA
  - Input (CreateJiraRequest):
    - summary: string
    - description: string
    - acceptanceCriteria: array of strings
    - assigneeId: string (Jira accountId)
  - Output (CreateJiraResponse): { "issueId": "...", "issueUri": "..." }

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
- Authorization: Ensure callers are in GROUP_MCP-CREATE-JIRA for Jira tools and GROUP_MCP-GET-WEATHER for weather
- Error handling: Jira-related errors surface as descriptive messages; verify group membership and Token Broker configuration if failures persist