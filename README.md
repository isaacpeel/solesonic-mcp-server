# solesonic-mcp-server

This project is configured to load environment variables from a local `.env` file at runtime using `spring-dotenv`.

## Local setup
1. Ensure you have JDK 24+ and Maven installed.
2. Create or edit the `.env` file at the project root (a template is already included but gitignored):

```
COGNITO_ISSUER_URI=https://example.auth.<region>.amazoncognito.com
# Optionally, if you configure the JWK set URI explicitly
# COGNITO_JWK_SET_URI=https://cognito-idp.<region>.amazonaws.com/<userPoolId>/.well-known/jwks.json
```

The application references these via Spring placeholders in `application.properties`:
```
spring.security.oauth2.resourceserver.jwt.issuer-uri=${COGNITO_ISSUER_URI}
```

No extra flags are needed; `.env` is picked up automatically at startup.

## Build and Run
```
# Build
mvn package

# Run (adjust the jar name if needed)
java -jar target/solesonic-mcp-server-0.0.1-SNAPSHOT.jar
```

Alternatively, you can query actuator endpoints (health/info) which are publicly accessible:
```
curl -s http://localhost:8080/actuator/health
```

> Note: All non-actuator endpoints require OAuth2 JWT with `SCOPE_mcp.invoke` per SecurityConfig.

## Notes
- `.env` is intended for local development only. Real OS environment variables override values from `.env`.
- The `.env` file is gitignored to prevent committing secrets.
