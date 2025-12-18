# Configuration

Overview
- Configuration is flat properties (no YAML). Defaults are in src/main/resources/*.properties
- A .env at the project root is loaded automatically by spring-dotenv
- Precedence: OS environment variables override .env values
- Activate profiles with SPRING_PROFILES_ACTIVE (e.g., prod,ssl)

Key properties (environment variables in parentheses)
- Application
  - spring.application.name=solesonic-mcp-server
  - server.port=9443
- Security (OAuth2 Resource Server - JWT)
  - spring.security.oauth2.resourceserver.jwt.issuer-uri=(${ISSUER_URI})
  - spring.security.oauth2.resourceserver.jwt.jwk-set-uri=(${JWK_SET_URI})
- MCP server identity (informational)
  - spring.ai.mcp.server.name=solesonic-mcp-server
  - spring.ai.mcp.server.version=1.0.0
  - spring.ai.mcp.server.type=sync
- Web Search (Tavily)
  - websearch.provider=tavily
  - tavily.api.key=(${TAVILY_API_KEY})
  - tavily.api.endpoint=(${TAVILY_API_ENDPOINT:https://api.tavily.com/search})
  - websearch.default.max-results=10
  - websearch.advanced.max-results=25
  - websearch.news.max-results=20
  - websearch.extract.max-urls=5
  - websearch.default.depth=basic   # basic|advanced (provider-specific)
- Jira tools
  - jira.api.uri=https://api.atlassian.com
  - jira.url.template=(${JIRA_URL_TEMPLATE})
  - solesonic.llm.jira.cloud.id.path=(${JIRA_CLOUD_ID_PATH})
- Atlassian Token Broker (external service)
  - atlassian.token.broker.uri=(${ATLASSIAN_TOKEN_BROKER_URL})
  - spring.security.oauth2.client.provider.atlassian-token-broker.token-uri=(${ATLASSIAN_TOKEN_BROKER_ISSUER_URI})
  - spring.security.oauth2.client.registration.atlassian-token-broker.client-id=(${ATLASSIAN_TOKEN_BROKER_CLIENT_ID})
  - spring.security.oauth2.client.registration.atlassian-token-broker.client-secret=(${ATLASSIAN_TOKEN_BROKER_CLIENT_SECRET})
  - spring.security.oauth2.client.registration.atlassian-token-broker.authorization-grant-type=client_credentials
  - spring.security.oauth2.client.registration.atlassian-token-broker.scope=(${ATLASSIAN_TOKEN_BROKER_SCOPES})

SSL profile (ssl)
- src/main/resources/application-ssl.properties
  - server.ssl.key-alias=tomcat
  - server.ssl.enabled-protocols=TLSv1.2,TLSv1.3
  - server.ssl.ciphers=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384

Examples
- Local overrides
  - export ISSUER_URI=https://<your-issuer>
  - export JWK_SET_URI=https://<your-domain>/.well-known/jwks.json
  - export TAVILY_API_KEY=<your-tavily-api-key>
  - export TAVILY_API_ENDPOINT=https://api.tavily.com/search
  - export SPRING_PROFILES_ACTIVE=prod,ssl
  - export SSL_CERT_LOCATION=/absolute/path/to/server.p12
  - export KEYSTORE_PASSWORD=<change-me>
  - java -jar target/solesonic-mcp-server-0.0.1.jar
- Docker Compose (.env example)
  - SSL_PUBLIC_CERT=/your/public/cert
  - SSL_PRIVATE_CERT=/your/private/cert

Notes
- Keep secrets out of source control; use OS env vars or Docker secrets/volumes.
- If both issuer-uri and jwk-set-uri are configured, this server uses the configured JWKS endpoint for validation.
- Web Search is optional; without `tavily.api.key` the Web Search tools will not function.
- Prompt behavior may include dynamic tool injection; no specific configuration is required, but tool feature flags affect what is injected.