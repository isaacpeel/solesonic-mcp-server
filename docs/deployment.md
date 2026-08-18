# Deployment

Local (JAR)
- Build: ./mvnw clean verify
- Run (default profile): java -jar target/solesonic-mcp-server-1.1.0.jar
- Run with profiles: java -Dspring.profiles.active=prod,ssl -jar target/solesonic-mcp-server-1.1.0.jar
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

PostgreSQL (workflow storage)
- Required at startup. Flyway runs the migrations in `src/main/resources/db/migration`, and the `comfy_workflow` table is read once during initialization to register the image generation tools.
- Configured via `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.
- **The database is not part of `docker/docker-compose.yml`** — that file provides only Redis and the server itself. Deployment currently assumes an externally managed PostgreSQL instance; provision one before first start.
- Verification from the host running this server:
  - `psql "$DATABASE_URL" -c 'select tool_name, enabled from comfy_workflow'`
- The table starts empty. Until you insert a workflow row, the server starts normally but registers no image generation tools — see Image Generation: ./image-generation.md

ComfyUI (image generation backend)
- The workflow tools call a ComfyUI instance running on the DGX Spark, reached over HTTPS at `COMFYUI_API_URI` (e.g. `https://comfy.izzy-bot.com`). Which model runs is a property of each stored workflow's `ckpt_name`, not of this server's configuration.
- ComfyUI is a separate deployment; this server only needs outbound HTTPS to it.
- Verification from the host running this server:
  - curl https://<comfyui-host>/system_stats (expect JSON naming a CUDA device)
- **Origin hardening.** Stock ComfyUI has no authentication. Anyone who can resolve and reach the hostname can execute arbitrary workflows, and — depending on `SECURITY_LEVEL` and whether ComfyUI-Manager is installed — potentially install code. The MCP tool's `@PreAuthorize` protects this server's surface and does nothing for the ComfyUI origin. Restrict that origin at the proxy with an IP allowlist or proxy-level authentication.

Notes
- Use trusted CA-signed certificates for production; self-signed certs require clients to skip verification or trust the CA.
- Ensure network policies allow inbound 9443 and outbound connectivity to your IdP, the Atlassian Token Broker, the PostgreSQL instance, and the ComfyUI instance.