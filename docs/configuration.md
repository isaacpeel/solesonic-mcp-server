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
  - spring.security.oauth2.resourceserver.jwt.issuer-uri=(${COGNITO_ISSUER_URI})
  - spring.security.oauth2.resourceserver.jwt.jwk-set-uri=(${COGNITO_JWK_SET_URI})
- MCP server identity (informational)
  - spring.ai.mcp.server.name=solesonic-mcp-server
  - spring.ai.mcp.server.version=1.0.0
  - spring.ai.mcp.server.type=sync
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
  - server.ssl.key-store=file:${SSL_CERT_LOCATION}
  - server.ssl.key-store-type=PKCS12
  - server.ssl.key-alias=tomcat
  - server.ssl.key-store-password=(${KEYSTORE_PASSWORD})
  - server.ssl.enabled-protocols=TLSv1.2,TLSv1.3
  - server.ssl.ciphers=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384

Examples
- Local overrides
  - export COGNITO_ISSUER_URI=https://<your-domain>.auth.<region>.amazoncognito.com
  - export COGNITO_JWK_SET_URI=https://<your-domain>.auth.<region>.amazoncognito.com/oauth2/jwks
  - export SPRING_PROFILES_ACTIVE=prod,ssl
  - export SSL_CERT_LOCATION=/absolute/path/to/server.p12
  - export KEYSTORE_PASSWORD=<change-me>
  - java -jar target/solesonic-mcp-server-0.0.1.jar
- Docker Compose (.env example)
  - COGNITO_ISSUER_URI=https://<your-domain>.auth.<region>.amazoncognito.com
  - COGNITO_JWK_SET_URI=https://<your-domain>.auth.<region>.amazoncognito.com/oauth2/jwks
  - SSL_CERT_LOCATION=/run/secrets/server.p12
  - KEYSTORE_PASSWORD=<change-me>

Notes
- Keep secrets out of source control; use OS env vars or Docker secrets/volumes.
- If both issuer-uri and jwk-set-uri are configured, this server uses the configured JWKS endpoint for validation.