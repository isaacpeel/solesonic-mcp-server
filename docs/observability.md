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

Logs as ECS JSON
- In the `prod` and `prod-nginx` profiles the application log is ECS JSON (`logging.structured.format.*`) in
  `/var/log/solesonic-mcp-server/solesonic-mcp-server.log`; each request's line carries its `trace.id`
- solesonic-log-agent ships that file (and `security.log`) to HyperDX

Metrics and tracing (OpenTelemetry)
- `spring-boot-starter-opentelemetry`: every request gets a trace id (in the MDC and as an outbound W3C
  `traceparent`, so llm-api -> mcp-server -> LiteLLM is one trace)
- Export is off by default (`application.properties`: `management.tracing.export.enabled=false`,
  `management.otlp.metrics.export.enabled=false`) and on in `prod-nginx`:
  - traces: `management.opentelemetry.tracing.export.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT}/v1/traces`
  - metrics: `management.otlp.metrics.export.url=${OTEL_EXPORTER_OTLP_ENDPOINT}/v1/metrics`, every 60 s
  - both send `authorization: ${HYPERDX_INGESTION_KEY}` (from `.env`); sampling is 100 %
  - default endpoint `http://otel-collector:4318`: the container joins the external `solesonic-hyperdx` network
    (see `docker/docker-compose.yml`)
- Spring AI observations never include prompt, completion or tool content
  (`spring.ai.*.observations.log-prompt/log-completion=false`, `spring.ai.tools.observations.include-content=false`)
- Logs are not exported over OTLP (`management.logging.export.otlp.enabled=false`); the file above is the source

Notes
- Avoid logging secrets or tokens; redact sensitive values