# Contributing

Development setup
- Prerequisites: JDK 24+, Maven 3.9+
- Build and test: ./mvnw clean verify
- Run locally: java -jar target/solesonic-mcp-server-0.0.1.jar

Coding guidelines
- Java, Spring Boot conventions; constructor injection for new beans
- Use SLF4J for logging; avoid printing secrets/tokens
- Keep methods small and focused; add Javadoc for non-obvious logic
- All if statements should have braces
- Use static imports for methods when helpful

Security and configuration
- Do not commit secrets
- Use .env for local convenience; OS env vars override

Testing
- Add tests for new behavior
- Run: ./mvnw -q -DskipTests=false test

Pull requests
- Describe the change and motivation
- Include documentation updates under docs/* when behavior or configuration changes
- Ensure CI passes (build and tests)

License
- See ./license.md and the root LICENSE file