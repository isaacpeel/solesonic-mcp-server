# Work in Progress

Thank you for your interest in solesonic-mcp-server. We are actively working on several enhancements to improve security and developer experience.

Planned additions (coming soon):

- Three‑legged OAuth (Authorization Code with PKCE)
  - Support for end-user consent flows in addition to client credentials
  - Example configuration and client walkthroughs
- Advanced tool examples
  - Multi-step/stateful tools, streaming responses, and external API integrations
  - Patterns for validation, error handling, and testing

Current status:
- MCP endpoint (/mcp) is protected by OAuth2 JWT bearer tokens with fine-grained authorization controls including scope-based and group-based authorities.
- Per-tool authorization is implemented using Spring Security's `@PreAuthorize` annotations with group-based access control (e.g., `GROUP_MCP-GET-WEATHER`).
- Atlassian tool authentication is stubbed; full per-tool authentication/authorization is coming soon.

## OAuth Token Chains

The server supports OAuth token chains for secure service-to-service communication:

- **Primary Authentication**: Client applications authenticate using JWT bearer tokens from AWS Cognito or compatible OAuth2 providers
- **Token Propagation**: Authenticated requests can include user context and group memberships for downstream service authorization
- **Group-based Authorization**: Fine-grained access control through Cognito groups mapped to Spring Security authorities (e.g., `GROUP_MCP-GET-WEATHER`, `GROUP_ADMIN`)
- **Scope Validation**: Standard OAuth2 scopes are validated alongside custom group authorities
- **Future Support**: Planned support for token exchange and delegation patterns for complex multi-service workflows

This architecture enables secure, scalable authorization patterns while maintaining compatibility with standard OAuth2 flows.

Status: In progress. Targeted updates will be pushed incrementally.

If you have specific requirements or would like to prioritize a scenario, please open an issue or start a discussion.

Last updated: 2025-08-28
