# Work in Progress

Thank you for your interest in solesonic-mcp-server. We are actively working on several enhancements to improve security and developer experience.

Planned additions (coming soon):

- Fine‑grained OAuth controls
  - More detailed, per-endpoint/per-tool authorization rules
  - Clear guidance and examples for scopes/authorities mapping
- Three‑legged OAuth (Authorization Code with PKCE)
  - Support for end-user consent flows in addition to client credentials
  - Example configuration and client walkthroughs
- Advanced tool examples
  - Multi-step/stateful tools, streaming responses, and external API integrations
  - Patterns for validation, error handling, and testing

Current status:
- Ingress (/mcp) is protected by basic OAuth (JWT bearer token) at the resource-server layer.
- Atlassian tool authentication is stubbed; full per-tool authentication/authorization is coming soon.

Status: In progress. Targeted updates will be pushed incrementally.

If you have specific requirements or would like to prioritize a scenario, please open an issue or start a discussion.

Last updated: 2025-08-19
