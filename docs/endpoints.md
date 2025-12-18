# Endpoints (MCP over HTTP)

Transport
- HTTP JSON-RPC 2.0 over a single endpoint
- Base path: POST /mcp
- All requests require Authorization: Bearer <JWT>

Initialize handshake (example)
- Request
```
POST /mcp HTTP/1.1
Host: localhost:9443
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "client": { "name": "inspector", "version": "0.1" },
    "capabilities": {}
  }
}
```

- Response (structure may vary)
```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "protocolVersion": "2024-11-05",
    "server": { "name": "solesonic-mcp-server", "version": "1.0.0" },
    "capabilities": { "tools": {} },
    "instructions": "...optional..."
  }
}
```

Tool discovery and invocation
- After initializing, clients can list tools and invoke them per MCP spec
- Tools carry names and JSON-serializable parameters
- Authorization applies per tool via method-level rules in the server

Web Search examples
- Basic search
```
POST /mcp
Authorization: Bearer <JWT with ROLE_MCP-WEB-SEARCH>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "20",
  "method": "tools/call",
  "params": { "name": "web_search", "arguments": { "query": "spring mcp" } }
}
```

- Advanced search
```
{
  "jsonrpc": "2.0",
  "id": "21",
  "method": "tools/call",
  "params": {
    "name": "web_search_advanced",
    "arguments": { "query": "oauth2 resource server", "timeRange": "m", "maxResults": 10 }
  }
}
```

- News search
```
{
  "jsonrpc": "2.0",
  "id": "22",
  "method": "tools/call",
  "params": { "name": "web_search_news", "arguments": { "query": "java 25", "timeRange": "w" } }
}
```

- Content extraction
```
{
  "jsonrpc": "2.0",
  "id": "23",
  "method": "tools/call",
  "params": { "name": "web_extract_content", "arguments": { "urls": ["https://spring.io/blog/..."] } }
}
```

Prompts discovery and invocation
- List prompts
```
{ "jsonrpc": "2.0", "id": "30", "method": "prompts/list", "params": {} }
```

- Get prompt
```
{ "jsonrpc": "2.0", "id": "31", "method": "prompts/get", "params": { "name": "basic-prompt" } }
```

- Execute prompt
```
{
  "jsonrpc": "2.0",
  "id": "32",
  "method": "prompts/execute",
  "params": { "name": "basic-prompt", "arguments": { "userMessage": "Help me plan a migration" } }
}
```

Notes
- Use HTTPS when the ssl profile is active (default Docker compose uses ssl)
- Ensure your JWT audience/issuer matches the server configuration (see Security)
- Web Search requires ROLE_MCP-WEB-SEARCH; other tools have their own role requirements. Prompts themselves do not require extra roles.