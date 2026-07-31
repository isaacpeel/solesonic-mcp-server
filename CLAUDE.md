# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test
Use intellij MCP tools to build and test.
Run configurations should be used when possible.
If a run configuration doesn't exist, create one.

JDK 25 + Netty requires `--enable-native-access=ALL-UNNAMED`. Surefire already sets it in `pom.xml`; add it as a VM option in IntelliJ run configurations and when running the jar directly.

Stack: Java 25, Spring Boot 4.0.6, Spring AI 2.0.0, A2A Java SDK 1.0.0.Final, LangGraph4j 1.8.17. Runtime dependencies: Redis (chat memory + A2A task store) and Ollama (`qwen3.5:9b`, `granite4.1:3b`, pulled `WHEN_MISSING`).

## Architecture

Four top-level packages under `com.solesonic`, layered rather than parallel:

- **`mcp/`** — the MCP protocol surface: tools, prompts, security, and typed HTTP clients for external APIs.
- **`agent/`** — LangGraph4j state graphs that implement multi-step workflows. MCP tools and A2A executors both invoke these.
- **`a2a/`** — the Agent2Agent server: agent cards, JSON-RPC/SSE endpoints, Redis-backed task store, agent executors.
- **`service/` + `model/`** — external integrations (Atlassian, ESPN, Tavily) and their record models, shared by both `mcp` and `agent`.

### MCP tools and prompts

Tools are plain `@Service` beans with `@McpTool`-annotated methods (`mcp/tool/**`); Spring AI's annotation scanning registers them — there is no `ToolCallbackProvider` bean. Each tool method carries `@PreAuthorize("hasAuthority('ROLE_MCP-...')")`, enforced by `@EnableMethodSecurity`. `SolesonicTool.availableTools(Class...)` reflects over `@McpTool` methods to build a tool list for prompt injection.

Prompts are `@McpPrompt` methods in `mcp/prompt/PromptProvider`, rendered from StringTemplate `.st` files under `src/main/resources/prompt/**` via `PromptUtil.buildPromptResult`. Graph nodes render the same `.st` files directly with Spring AI `PromptTemplate`.

Destructive tools elicit confirmation through `McpConfirmations.confirm(...)` and branch on the `ElicitResult.Action` (ACCEPT/DECLINE/CANCEL). Long-running tools stream progress via `ProgressReporter` over `McpSyncRequestContext`.

### LangGraph4j graph conventions

Every workflow follows the same shape:

- **State** (`SportsState`, `JiraState`, `AgileState`) extends `AgentState`: `public static final String` key constants plus `Optional<T>` accessor methods. Never read state by string literal.
- **Nodes** are `@Component`s implementing `AsyncNodeAction<State>`, returning a `CompletableFuture<Map<String, Object>>` containing only the keys they changed.
- **Graphs** are `@Bean CompiledGraph<State>` in `@Configuration` classes, with node names as `public static final String` constants so callers can map node output to progress percentages. Multiple graphs of the same type coexist, so injection sites use `@Qualifier("nbaScheduleGraph")` etc.
- `NbaOrchestratorGraphConfig` composes the six NBA sub-graphs: `ParseSportsIntentNode` classifies the question, a conditional edge routes single-intent queries straight to one sub-graph (each of which synthesizes its own answer), and multi-intent queries fan out and then meta-synthesize.

### A2A server

Agent cards are declarative: `src/main/resources/agents/*.json` is loaded by `AgentCardService`, which builds each `AgentCard` URL from the *current request's* base URI so cards work behind proxies. An agent's executor is an `AgentExecutor` `@Component` whose **bean name is the agent id** (`@Component("nba")`); `AgentRequestHandlerRegistry` maps bean name → `DefaultRequestHandler`, and `/a2a/{agentName}` resolves handlers by that name. Adding an agent means: a card JSON + an executor bean whose name matches the card `id`.

Both endpoints on `/a2a/{agentName}` are POST; content negotiation on `produces` picks JSON-RPC vs. SSE streaming. Task state, event queues, and push-notification configs are all Redis-backed (`a2a/redis/**`), so restarts don't lose tasks. `a2aExecutor` uses virtual threads.

### Security

OAuth2 Resource Server (JWT). `AuthoritiesService` maps the `groups` claim to `GROUP_<NAME>` and the `roles` claim to `ROLE_<NAME>` (uppercased); scopes become `SCOPE_*`. `/.well-known/oauth-protected-resource` and `/a2a/**/.well-known/agent-card.json` are public; everything else authenticates. The whole `MpcSecurityConfig` is gated on `solesonic.agent.security.enabled` (`MpcSecurityDisabledConfig` is the alternative).

`SecurityContextHolder` is set to `MODE_INHERITABLETHREADLOCAL` because Atlassian calls happen on graph/async threads: `AtlassianRequestAuthorizationFilter` is a WebClient `ExchangeFilterFunction` that reads the JWT subject off the security context, exchanges it for a short-lived Atlassian token via the token broker, and injects the `Authorization` header. Breaking context propagation breaks all Jira/Confluence tooling.

### Configuration

Flat `.properties` only (no YAML). `application.properties` holds defaults with `${ENV_VAR}` placeholders; profile files are `local`, `prod` (port 9443, actuator off), `prod-nginx` (forwarded headers), and `ssl` (PEM cert paths under `/run/secrets`). A root `.env` is loaded by spring-dotenv; OS environment variables take precedence. Tests use `@ActiveProfiles("test")` with dummy values in `src/test/resources/application-test.properties` — new required env vars must be added there or `contextLoads` fails.

Jackson 3 (`tools.jackson`) is the mapper API; only annotations come from `com.fasterxml.jackson.annotation`. Inject the configured `JsonMapper` bean rather than constructing one.

## Documentation

`docs/` is user-facing reference (endpoints, tools, security, configuration, prompts, deployment). Update the relevant page when behavior or configuration changes.
