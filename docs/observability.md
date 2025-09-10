# Observability

Health and info
- Spring Boot Actuator endpoints may be available depending on your management configuration
- Example (if exposed): curl -ik https://localhost:9443/actuator/health

Logging
- Logs are written to STDOUT by default
- Increase log level at runtime via env:
  - export LOGGING_LEVEL_ROOT=INFO
  - export LOGGING_LEVEL_COM_SOLESONIC=DEBUG
- In Docker, use docker logs -f solesonic-mcp-server

Metrics and tracing
- Not explicitly configured in this repository; you can add Micrometer/OTel as needed

Notes
- Avoid logging secrets or tokens; redact sensitive values