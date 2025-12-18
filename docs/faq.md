# FAQ

Why HTTP MCP vs stdio?
- HTTP simplifies deployment, security (JWT), and observability
- Works well with reverse proxies and standard infrastructure

Can I put this behind a reverse proxy?
- Yes. Terminate TLS at a proxy if desired and forward to 9443
- Preserve Authorization header and pass-through /mcp

How do I rotate SSL keystores?
- Provide a new PKCS12 file and update KEYSTORE_PASSWORD if needed
- In Docker, replace the mounted file and restart the container

How do I configure audience and scope?
- This repo maps scope claims to SCOPE_<scope> authorities
- There is no fixed audience enforced here; ensure your IdP issues tokens compatible with your issuer/JWKS configuration and include the groups/scopes you plan to rely on

Are health/info endpoints public or protected?
- All paths require authentication by default in this repository
- You can relax actuator endpoint security via standard Spring Boot management configuration if needed

Web Search
- Can I use web search without a Tavily account?
  - No. A valid Tavily API key is required; configure `TAVILY_API_KEY`.
- Are there rate limits on web searches?
  - Yes. Tavily enforces rate limits; implement retries with backoff and keep result counts modest.
- What happens if content extraction fails on a URL?
  - The response includes per-URL status and a message. Other URLs in the same request may still succeed.

Prompts
- What is the difference between prompts and tools?
  - Prompts return agent-ready instructions and context; tools perform actions. Prompts often recommend which tools to call next.
- How do I discover available prompts from the server?
  - Call `prompts/list` after `initialize`. Use `prompts/get` to see details and `prompts/execute` to render.
- Can I create custom prompts?
  - Yes, by adding new server-side prompt definitions (e.g., `@McpPrompt`). Client support for prompts is required.
- Do prompts require separate authorization from tools?
  - No additional role is required to access prompts; however, tools referenced by prompts enforce their own authorization (e.g., ROLE_MCP-WEB-SEARCH).

Elicitation
- What is elicitation and when should I use it?
  - Elicitation is a guided, multi-turn process to clarify requirements. Use it when tasks are ambiguous or prior to creating structured outputs like Jira issues.
- Can I combine elicitation with other prompts?
  - Yes. A common pattern is elicitation → create-jira-issue or elicitation → create-confluence-page.