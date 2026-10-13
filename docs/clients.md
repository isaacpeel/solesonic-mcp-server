# Clients

MCP Inspector
- Quick test client for HTTP MCP servers
- Command:
  - npx @modelcontextprotocol/inspector --server-url https://localhost:9443/mcp --header "Authorization: Bearer <YOUR_JWT>"

Claude Desktop (example)
- mcp.json example using an environment variable for the bearer token
```
{
  "mcpServers": {
    "solesonic": {
      "transport": {
        "type": "http",
        "url": "https://localhost:9443/mcp",
        "headers": {
          "Authorization": "Bearer ${MCP_BEARER_TOKEN}"
        }
      }
    }
  }
}
```
- Set MCP_BEARER_TOKEN in your shell before launching Claude Desktop

opencode (OAuth with Dynamic Client Registration)
- opencode discovers the authorization server from the 401 challenge, registers its own public client in Keycloak, and logs you in through the browser with PKCE. Tokens are refreshed automatically and carry your own identity, so per-user tools (Jira, Gmail) work.
- Requires the Keycloak anonymous-DCR setup described in ./authorization.md.
- `~/.config/opencode/opencode.json` (no `oauth` or `headers` keys, so opencode runs discovery and registration itself):
```
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "solesonic": {
      "type": "remote",
      "url": "https://mcp.izzy-bot.com/mcp",
      "enabled": true,
      "timeout": 30000
    }
  }
}
```
- Log in once: `opencode mcp auth solesonic`. Check with `opencode mcp list`.
- Each login after `opencode mcp logout solesonic` registers a new client in Keycloak. Prune unused ones from Clients occasionally.

Headers and tokens
- Always pass Authorization: Bearer <JWT>
- If using self-signed certs in dev, your client must trust the cert or skip verification
- Ensure the server is running with the `ssl` profile for HTTPS (enabled by default in Docker Compose).