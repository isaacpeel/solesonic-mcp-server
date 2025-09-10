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
- After initialize, clients can list tools and invoke them per MCP spec
- Tools carry names and JSON-serializable parameters
- Authorization applies per tool via method-level rules in the server

Notes
- Use HTTPS when the ssl profile is active (default Docker compose uses ssl)
- Ensure your JWT audience/issuer matches the server configuration (see Security)