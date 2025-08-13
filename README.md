# solesonic-mcp-server

This project is configured to load environment variables from a local `.env` file at runtime using `spring-dotenv`.

## Local setup
1. Ensure you have JDK 24+ and Maven installed.
2. Create or edit the `.env` file at the project root (a template is already included but gitignored):

```
COGNITO_ISSUER_URI=https://example.auth.<region>.amazoncognito.com
SOME_API_KEY=<your-api-key-here>
# Optionally, if you configure the JWK set URI explicitly
# COGNITO_JWK_SET_URI=https://cognito-idp.<region>.amazonaws.com/<userPoolId>/.well-known/jwks.json
```

The application references these via Spring placeholders in `application.properties`:
```
spring.security.oauth2.resourceserver.jwt.issuer-uri=${COGNITO_ISSUER_URI}
custom.api.key=${SOME_API_KEY}
```

No extra flags are needed; `.env` is picked up automatically at startup.

## Build and Run
```
# Build
mvn -q -DskipTests package

# Run (adjust the jar name if needed)
java -jar target/solesonic-mcp-server-0.0.1-SNAPSHOT.jar
```

## Verify .env loading
On startup, the application logs the resolved value for `COGNITO_ISSUER_URI` and whether `SOME_API_KEY` is present, e.g.:
```
[dotenv] COGNITO_ISSUER_URI resolved to: https://example.auth.us-east-1.amazoncognito.com
[dotenv] SOME_API_KEY present: true
```

Alternatively, you can query actuator endpoints (health/info) which are publicly accessible:
```
curl -s http://localhost:8080/actuator/health
```

> Note: All non-actuator endpoints require OAuth2 JWT with `SCOPE_mcp.invoke` per SecurityConfig.

## Notes
- `.env` is intended for local development only. Real OS environment variables override values from `.env`.
- The `.env` file is gitignored to prevent committing secrets.
