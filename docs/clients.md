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

Headers and tokens
- Always pass Authorization: Bearer <JWT>
- If using self-signed certs in dev, your client must trust the cert or skip verification
- Ensure the server is running with the `ssl` profile for HTTPS (enabled by default in Docker Compose).