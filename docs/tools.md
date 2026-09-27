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

- Gmail Tools
  - list_gmail_messages
    - Description: Lists the most recent messages in the caller's Gmail inbox, newest first. Message bodies are never returned — call `get_gmail_message_body` with a returned id to read one message's contents.
    - Auth: ROLE_MCP-GMAIL-LIST
    - Input: { "maxResults": <int, optional> } — defaults to 10, clamped to 1..25
    - Output: `GmailMessageListResponse` — `{ "note": "<string|null>", "messages": [{ "id", "subject", "from", "date" }, ...] }`. `note` is `null` on a normal result; it carries an explanatory sentence (and an empty `messages` array) for an empty inbox or an unconnected Google account rather than an error. The model decides how to present the data — this server returns structured data, not pre-formatted prose or Markdown.
    - Requires: the caller must have connected their Google account through solesonic-llm-api's consent flow (`GET /google/auth/uri`). If they have not, the tool answers with a `note` asking them to connect it.
  - list_gmail_messages_by_label
    - Description: Lists the most recent messages under a specific Gmail label, newest first — a system label like `STARRED`/`IMPORTANT`, or a user-created label by its display name. Does not return message bodies — call `get_gmail_message_body` with a returned id to read one message's contents.
    - Auth: ROLE_MCP-GMAIL-LIST
    - Input: { "label": "<string>", "maxResults": <int, optional> } — defaults to 10, clamped to 1..25
    - Output: Same `GmailMessageListResponse` shape as `list_gmail_messages`; an unrecognized label, an empty result, and an unconnected Google account each surface as a `note` rather than an error
    - Requires: same Google account connection as `list_gmail_messages`.
  - get_gmail_message_body
    - Description: Returns the body of a single Gmail message by id, for use after `list_gmail_messages` or `list_gmail_messages_by_label` has surfaced that id. Requests Gmail's `format=full` and walks the MIME tree for the best text part: the first `text/plain`, or the first `text/html` when the message carries no plain-text alternative.
    - Auth: ROLE_MCP-GMAIL-LIST — the same authority as the listing tools, so granting it gives read access to message content, not only to subjects and senders
    - Input: { "messageId": "<string>", "maxCharacters": <int, optional> } — `maxCharacters` defaults to 20000, clamped to 1000..100000
    - Output: `GmailMessageBodyResponse` — `{ "note": "<string|null>", "message": { "id", "subject", "from", "date", "mimeType", "body" } }`. The body is returned **verbatim**, exactly as the chosen MIME part carried it; this server does not convert HTML to plain text, so `mimeType` tells the caller whether `body` is `text/plain` or raw `text/html` markup. `note` is `null` on a normal result; it carries an explanatory sentence (with `message` null) for an unknown id, a message with no readable text part, or an unconnected Google account. A body longer than the character limit is the one case that returns a `note` *and* the data — the note says it was truncated.
    - Requires: same Google account connection as `list_gmail_messages`.

- Xero Tools
  - convert_email_to_xero_proposal
    - Description: Converts a single Gmail message into a draft Xero proposal. **Mocked** — no real Xero account is contacted yet; this stands up the tool contract ahead of a real Xero integration.
    - Auth: ROLE_MCP-XERO
    - Input: { "messageId": "<string>" } — a Gmail message id, from the `id` field of a `list_gmail_messages` / `list_gmail_messages_by_label` result
    - Output: A mock proposal summary prefixed `[MOCK]`; an unresolvable message id or an unconnected Google account each answer with an explanatory sentence rather than an error
    - Requires: same Google account connection as the Gmail tools, since it reads the referenced email through them.

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

- Image Generation Tools
  - These tools are **defined by data, not by code**. Each enabled row in the `comfy_workflow` table is registered as one MCP tool at startup, named by its `tool_name` column, so the tool list depends on the database and the set below is not fixed. If the table is empty, no image tools are registered.
  - One tool per stored ComfyUI workflow
    - Description: Generates an image from a text prompt using a self-hosted ComfyUI instance and returns it inline as a PNG. Typically 5–15 seconds; progress notifications are emitted while the job runs. Each tool carries its row's `description`, which is what a model reads when choosing between workflows.
    - Auth: ROLE_MCP-GENERATE-IMAGE (all workflow tools)
    - Input: { "prompt": "<string>", "width": <int, optional>, "height": <int, optional> } — `width` and `height` appear only if the stored workflow tokenises them, and default to 1024. Steps, cfg, sampler, and the checkpoint are baked into the stored workflow, so choosing a tool is how you choose them.
    - Output: CallToolResult carrying an ImageContent (base64 PNG, `image/png`) plus a text block with the workflow name, size, seed, and elapsed time
  - Details, including the substitution tokens and how to add a workflow: See Image Generation docs: ./image-generation.md

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

Google Token Broker exchange (used by Gmail tools)
- Purpose: Obtain short-lived Google access tokens without this server ever holding a Google refresh token
- Flow:
  1) This server authenticates to the broker with the same OAuth2 client credentials registration as the Atlassian broker (registration id: atlassian-token-broker). One service client fronts both broker endpoints, so its service account needs the `token-mint-gmail` role in addition to `token-mint-jira`.
  2) For a given end-user (subject), it posts a GoogleTokenExchange payload to google.token.broker.uri: { "subject_token": "<UUID>" }. There is no `audience` field — the endpoint identifies the provider.
  3) The broker returns GoogleTokenResponse: { "accessToken": "...", "expiresInSeconds": 3600, "issuedAt": "<ISO8601>", "userId": "<UUID>" }
  4) The accessToken is used for downstream Gmail API calls
- Errors: the broker answers with a real status code and a { "code", "message" } body. `RECONNECT_REQUIRED` (400) means the user has never connected Google or has revoked it.
- Caching: tokens are cached per user for their lifetime minus a 60 second skew. Listing an inbox costs one Gmail call per message, and the authorization filter runs on every one of them, so an uncached exchange would hit the broker a dozen times per tool call.
- Configuration:
  - google.api.uri
  - google.token.broker.uri
  - the shared spring.security.oauth2.client.registration.atlassian-token-broker.* entries

Operational guidance
- Idempotency: The create_jira_issue tool should not be called repeatedly for the same request; consider upstream guards to prevent duplicates
- Authorization: Ensure callers have the required ROLE_ authorities:
  - Jira: ROLE_MCP-JIRA-CREATE, ROLE_MCP-JIRA-GET, ROLE_MCP-JIRA-DELETE
  - Agile: ROLE_MCP-JIRA-AGILE-LIST
  - Gmail: ROLE_MCP-GMAIL-LIST (covers message bodies as well as listing)
  - Web Search: ROLE_MCP-WEB-SEARCH
  - Image Generation: ROLE_MCP-GENERATE-IMAGE
  - Date/Time: ROLE_MCP-TIME
  - Weather (demo): ROLE_MCP-GET-WEATHER
- Error handling: Jira-related errors surface as descriptive messages; verify role membership and Token Broker configuration if failures persist

See also
- Web Search: ./web-search.md
- Image Generation: ./image-generation.md
- Prompts: ./prompts.md
