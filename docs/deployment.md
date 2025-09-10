# Deployment

Local (JAR)
- Build: ./mvnw clean verify
- Run (default profile): java -jar target/solesonic-mcp-server-0.0.1.jar
- Run with profiles: java -Dspring.profiles.active=prod,ssl -jar target/solesonic-mcp-server-0.0.1.jar
- Port: 9443

Docker Compose
- Start (build + run):
  - docker compose -f docker/docker-compose.yml up --build -d
- Ports:
  - Host 9443 → Container 9443
- Environment and secrets:
  - .env at project root is read; OS env vars override
  - PKCS12 keystore mounted via volume as /run/secrets/server.p12
- Stop and remove:
  - docker compose -f docker/docker-compose.yml down

Production with SSL
- Enable profiles: prod,ssl
- SSL configuration (application-ssl.properties):
  - server.ssl.key-store=file:${SSL_CERT_LOCATION}
  - server.ssl.key-store-type=PKCS12
  - server.ssl.key-alias=tomcat
  - server.ssl.key-store-password=${KEYSTORE_PASSWORD}
  - server.ssl.enabled-protocols=TLSv1.2,TLSv1.3
  - server.ssl.ciphers=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384
- Docker Compose example (.env):
  - SSL_CERT_LOCATION=/run/secrets/server.p12
  - KEYSTORE_PASSWORD=<change-me>
- Verification:
  - curl -ik https://localhost:9443/mcp (expect 401 if no token)
  - With MCP Inspector: npx @modelcontextprotocol/inspector --server-url https://localhost:9443/mcp --header "Authorization: Bearer <JWT>"

Notes
- Use trusted CA-signed certificates for production; self-signed certs require clients to skip verification or trust the CA.
- Ensure network policies allow inbound 9443 and outbound connectivity to your IdP and Atlassian Token Broker.