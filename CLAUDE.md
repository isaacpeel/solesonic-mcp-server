# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test
Use intellij MCP tools to build and test.
Run configurations should be used when possible.
If a run configuration doesn't exist, create one.

JDK 25 + Netty requires `--enable-native-access=ALL-UNNAMED`. Surefire already sets it in `pom.xml`; add it as a VM option in IntelliJ run configurations and when running the jar directly.

Stack: Java 25, Spring Boot 4.0.6, Spring AI 2.0.1, LangGraph4j 1.8.17. Runtime dependencies: Redis (chat memory + graph checkpoints) and the OpenAI API (`OPENAI_API_KEY`; models selected per chat client in `agent/config/*ChatClientConfig`).

## Architecture

Three top-level packages under `com.solesonic`, layered rather than parallel:

- **`mcp/`** — the MCP protocol surface: tools, prompts, security, and typed HTTP clients for external APIs.
- **`agent/`** — LangGraph4j state graphs that implement multi-step workflows, invoked by MCP tools.
- **`service/` + `model/`** — external integrations (Atlassian, Tavily, ComfyUI, Gmail) and their record models, shared by both `mcp` and `agent`.

The NBA agent and the A2A server it was served by moved to `solesonic-agents` (`agents/nba-agent`, Python). This server has no A2A endpoints.

### MCP tools and prompts

Tools are plain `@Service` beans with `@McpTool`-annotated methods (`mcp/tool/**`); Spring AI's annotation scanning registers them — there is no `ToolCallbackProvider` bean. Each tool method carries `@PreAuthorize("hasAuthority('ROLE_MCP-...')")`, enforced by `@EnableMethodSecurity`. `SolesonicTool.availableTools(Class...)` reflects over `@McpTool` methods to build a tool list for prompt injection.

Prompts are `@McpPrompt` methods in `mcp/prompt/PromptProvider`, rendered from StringTemplate `.st` files under `src/main/resources/prompt/**` via `PromptUtil.buildPromptResult`. Graph nodes render the same `.st` files directly with Spring AI `PromptTemplate`.

Destructive tools elicit confirmation through `McpConfirmations.confirm(...)` and branch on the `ElicitResult.Action` (ACCEPT/DECLINE/CANCEL). Long-running tools stream progress via `ProgressReporter` (`mcp/progress`) over `McpSyncRequestContext`.

### LangGraph4j graph conventions

Every workflow follows the same shape:

- **State** (`JiraState`, `AgileState`, `ImageWorkflowState`) extends `AgentState`: `public static final String` key constants plus `Optional<T>` accessor methods. Never read state by string literal.
- **Nodes** are `@Component`s implementing `AsyncNodeAction<State>`, returning a `CompletableFuture<Map<String, Object>>` containing only the keys they changed.
- **Graphs** are `@Bean CompiledGraph<State>` in `@Configuration` classes, with node names as `public static final String` constants so callers can map node output to progress percentages. Multiple graphs of the same type coexist, so injection sites use `@Qualifier(...)`.

### Security

OAuth2 Resource Server (JWT). `AuthoritiesService` maps the `groups` claim to `GROUP_<NAME>` and the `roles` claim to `ROLE_<NAME>` (uppercased); scopes become `SCOPE_*`. `/.well-known/oauth-protected-resource` is public; everything else authenticates. The whole `MpcSecurityConfig` is gated on `solesonic.agent.security.enabled` (`MpcSecurityDisabledConfig` is the alternative).

Outbound per-user calls never read `SecurityContextHolder` — graph walks run on `ForkJoinPool` threads that don't inherit it. Entry points (`@McpTool` methods) capture `CallerIdentity.requireCurrent()` once; graphs carry it as `IdentifiedAgentState.CALLER_IDENTITY` (every state extends that base); every Atlassian/Gmail service method takes it as its first parameter and sets `.attributes(callerIdentity.requestAttributes())`. `AtlassianRequestAuthorizationFilter` / `GoogleRequestAuthorizationFilter` read it from the request attribute, broker a fresh token, and fail loudly if it's missing. `AtlassianErrorResponseFilter` turns non-2xx Atlassian responses into `JiraException`s (services use `exchangeToMono`, which bypasses default status handlers).

### Graph checkpoints

Every graph compiles with the shared `graphCompileConfig` (`agent/checkpoint/CheckpointConfig`): a Redis `RedisSaver` (Redisson, keys under `mcp:checkpoint:`, Jackson 3 state serializer with an allow-listed type registry) wrapped in `CleaningCheckpointSaver`, which deletes a thread's keys on release. Entry points run graphs only through `GraphRunner` (discards checkpoints on failure) with a `GraphThread` config: `GraphThread.conversation(...)` for graphs that pause and resume (Jira create), `GraphThread.run(...)` for one-shot graphs. Code that runs sub-graphs by hand uses `GraphThread.childConfig(...)`. To add a graph: a `GRAPH_NAME` constant, `.compile(graphCompileConfig)`, and runs through `GraphRunner`.

### Configuration

Flat `.properties` only (no YAML). `application.properties` holds defaults with `${ENV_VAR}` placeholders; profile files are `local`, `prod` (port 9443, actuator off), `prod-nginx` (forwarded headers), and `ssl` (PEM cert paths under `/run/secrets`). A root `.env` is loaded by spring-dotenv; OS environment variables take precedence. Tests use `@ActiveProfiles("test")` with dummy values in `src/test/resources/application-test.properties` — new required env vars must be added there or `contextLoads` fails.

Jackson 3 (`tools.jackson`) is the mapper API; only annotations come from `com.fasterxml.jackson.annotation`. Inject the configured `JsonMapper` bean rather than constructing one.

## Documentation

`docs/` is user-facing reference (endpoints, tools, security, configuration, prompts, deployment). Update the relevant page when behavior or configuration changes.
