# FAQ

Why HTTP MCP vs stdio?
- HTTP simplifies deployment, security (JWT), and observability
- Works well with reverse proxies and standard infrastructure

Can I put this behind a reverse proxy?
- Yes. Terminate TLS at a proxy if desired and forward to 9443
- Preserve Authorization header and pass-through /mcp

How do I rotate SSL keystores?
- Provide a new PKCS12 file and update KEYSTORE_PASSWORD if needed
- In Docker, replace the mounted file and restart the container

How do I configure audience and scope?
- This repo maps scope claims to SCOPE_<scope> authorities
- There is no fixed audience enforced here; ensure your IdP issues tokens compatible with your issuer/JWKS configuration and include the groups/scopes you plan to rely on

Are health/info endpoints public or protected?
- All paths require authentication by default in this repository
- You can relax actuator endpoint security via standard Spring Boot management configuration if needed