# Image Generation

Overview
- Image generation tools are **defined by data**. Each enabled row in the `comfy_workflow` table becomes one MCP tool at startup, so a client chooses a workflow by choosing a tool.
- All of them are backed by a self-hosted [ComfyUI](https://github.com/comfyanonymous/ComfyUI) instance.
- Authorization: callers must have `ROLE_MCP-GENERATE-IMAGE` for every workflow tool.
- The image comes back **inline** as MCP `ImageContent` (base64 PNG), not as a URL. Nothing is stored server-side.
- There is no fixed `generate_image` tool. If the table is empty, no image tools are registered and the rest of the server starts normally.

Tool shape

Each workflow tool is named by its row's `tool_name` column, titled by `name`, and described by `description` — the text a model reads when deciding which workflow to use, so it is worth writing carefully.

- Input:
  - `prompt` (string, required) — subject, style, lighting, composition. Longer and more specific is better.
  - `width` (integer, optional) — only advertised if the workflow contains `__WIDTH__`. Defaults to `1024`.
  - `height` (integer, optional) — only advertised if the workflow contains `__HEIGHT__`. Defaults to `1024`.
- Output: a `CallToolResult` with two content blocks:
  1. `ImageContent` — base64-encoded PNG, `mimeType` `image/png`
  2. `TextContent` — workflow name, size, seed, and elapsed seconds

A blank or missing prompt is rejected with an error result **before** any call to ComfyUI.

The defaults for `width` and `height` live in `ImageGenerationRequest`, in code — the table holds no tuning values at all.

Substitution tokens

A stored workflow is a plain ComfyUI API-format export carrying literal placeholder strings where caller-supplied values belong. Nothing else about the document is special: node ids, titles and layout are irrelevant.

| Token | Required | Replaced with | JSON type written |
|---|---|---|---|
| `__PROMPT__` | yes | the caller's prompt | string |
| `__SEED__` | no | a fresh random seed per call | number |
| `__WIDTH__` | no | the `width` argument, or `1024` | number |
| `__HEIGHT__` | no | the `height` argument, or `1024` | number |

Tokens are replaced **wherever they appear**, with correctly typed JSON — a seed lands as a number, never as the string `"__SEED__"`, so ComfyUI receives exactly what a hand-edited workflow would have produced.

The tokens a row carries determine its tool's input schema. A workflow that pins its own resolution simply omits `__WIDTH__` and `__HEIGHT__`, and its tool does not advertise those parameters — a client is never offered a knob that would be accepted and then silently ignored.

Everything not tokenised is left exactly as stored. That is what makes a row a preset rather than a set of knobs:

| Baked into the stored JSON | Example |
|---|---|
| `steps` | `4` for FLUX.1-schnell, which is distilled for it |
| `cfg` | `1` for schnell; raising it degrades output |
| `sampler_name`, `scheduler`, `denoise` | `euler`, `simple`, `1` |
| `batch_size` | `1` |
| negative prompt | inert at `cfg=1`, meaningful for other models |
| `ckpt_name` | the model itself — effectively what defines the row |
| `filename_prefix` | output naming on the ComfyUI host |

Two models, or one model at two step counts, are **two rows** rather than one row with more parameters.

Seed
- A workflow containing `__SEED__` draws a fresh random seed on every call, so repeating the same prompt gives a different image.
- The seed used is reported in the text block for traceability — it identifies the run in ComfyUI's history — but it is not a caller-supplied knob, so a specific image cannot be regenerated through the tool.
- Omitting `__SEED__` is a deliberate choice for a deterministic workflow: every call with the same prompt then returns the identical image, and ComfyUI's execution cache will serve the repeats.

Why there is usually no negative prompt or guidance scale
- The stock FLUX.1-schnell workflow pins `cfg` to `1`. schnell is a distilled model that expects it.
- With `cfg` at 1 there is no classifier-free guidance, so negative conditioning has no effect. The workflow does contain a negative `CLIPTextEncode` node, but it is inert and left with an empty string.
- Neither is tokenised, so neither is exposed. A workflow for a model that *does* use guidance would simply bake a different `cfg` — still not a caller parameter, because it belongs to the workflow.

Adding a workflow

Rows are inserted by hand; there is no seeding migration and no admin endpoint.

1. Build the workflow in the ComfyUI editor.
2. Use **Export (API)** — not plain Export. The two formats differ and only the API one is accepted by `POST /prompt`.
3. Replace the values you want callers to control with tokens: set the positive prompt's `text` to `__PROMPT__`, and optionally the sampler's seed to `__SEED__` and the latent node's dimensions to `__WIDTH__` / `__HEIGHT__`.
4. Do **not** wrap the graph in a `{"prompt": ...}` envelope; Java builds that envelope.
5. Insert the row and restart the server.

```sql
INSERT INTO comfy_workflow (name, tool_name, description, workflow_json) VALUES (
    'FLUX.1-schnell',
    'generate_image_flux_schnell',
    'Creates a brand new image from a natural-language description and returns it inline as a PNG. '
    'Fast photoreal and illustrative generation at 1024x1024, roughly 5-15 seconds. '
    'This tool only creates images; it cannot edit, resize, or annotate an existing image.',
    '<paste the tokenised API-format export here>'
);
```

`src/main/resources/comfyui/flux1-schnell.json` is kept in the repository as a tokenised reference copy you can paste from. It is **not loaded at runtime** — the database is the only source of truth.

`tool_name` must match `^[a-zA-Z0-9_-]+$` and is unique. It is what the client calls, so treat it as an identifier.

Validation and startup behaviour

Each row is parsed and validated independently at startup. A row is **skipped with a warning** — not fatal — if it:
- is not valid JSON,
- is not a JSON object,
- contains no `__PROMPT__` token, or
- has a `tool_name` that is not a legal MCP tool name.

Rows are typed in by hand, so one malformed paste must not be able to stop the server booting and take every unrelated tool down with it. Check the startup log to confirm what actually registered:

```
INFO  Registering ComfyUI workflow tool 'generate_image_flux_schnell' (FLUX.1-schnell)
INFO  Loaded 1 ComfyUI workflow(s): [generate_image_flux_schnell]
INFO  Registered 1 ComfyUI workflow tool(s)
```

Note this differs from the rest of the configuration surface, which fails fast. An unreachable *database* still fails startup — it is only a bad row that is tolerated.

The ComfyUI contract
The service speaks ComfyUI's native three-call protocol:

1. `POST /prompt` — submits the patched API-format workflow with a per-request `client_id`.
   ComfyUI answers **HTTP 200 with a populated `node_errors` object** when the workflow is malformed, so the service checks `node_errors` explicitly rather than trusting the status code.
2. `GET /history/{promptId}` — polled every `comfyui.generation.poll-interval-millis`. While the job is pending or running the response is `{}`; that emptiness is the "not done" signal. `status.status_str == "error"` fails the call.
3. `GET /view?filename=&subfolder=&type=` — downloads the rendered PNG, which is then base64-encoded.

Every individual HTTP call is short (`comfyui.api.response-timeout-seconds`, default 30s). The long wait is the poll loop, bounded separately by `comfyui.generation.timeout-seconds` (default 180s). A wedged instance therefore fails on the first poll rather than tying up a socket.

The `SaveImage` node is not bound — the service locates the output image by scanning the history entry's outputs for the first node that produced images.

Progress
- Workflow tools emit MCP progress notifications while they wait: submitting (5%), queued with the prompt id (15%), a time-based ramp capped at 85%, downloading (90%), and a final 100% naming the size, elapsed time, and seed.
- The ramp is an estimate derived from `comfyui.generation.expected-seconds`, not real per-step progress. It is capped below completion so it never claims the image is ready early.

Configuration

| Property | Default | Purpose |
|---|---|---|
| `comfyui.api.uri` | `${COMFYUI_API_URI}` | Base URI of the ComfyUI instance |
| `comfyui.api.response-timeout-seconds` | `30` | Timeout for each individual HTTP call |
| `comfyui.generation.timeout-seconds` | `180` | Deadline for a whole generation |
| `comfyui.generation.poll-interval-millis` | `1000` | Delay between history polls |
| `comfyui.generation.expected-seconds` | `12` | Drives the progress ramp |
| `spring.datasource.url` | `${DATABASE_URL}` | Postgres holding `comfy_workflow` |

Environment variables
- `COMFYUI_API_URI` — e.g. `https://comfy.izzy-bot.com`
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`

Examples (MCP JSON-RPC)

```
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "generate_image_flux_schnell",
    "arguments": { "prompt": "a lighthouse on a cliff in a storm, dramatic lighting, photorealistic" }
  }
}
```

With an explicit size, for a workflow that tokenises its dimensions:

```
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "generate_image_flux_schnell",
    "arguments": { "prompt": "a portrait of a fox in a library", "width": 832, "height": 1216 }
  }
}
```

Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| No image tools in `tools/list` | Table empty, or every row was skipped | Check the startup log for `Skipping ComfyUI workflow` warnings |
| Log: "workflow has no `__PROMPT__` token" | The export was pasted without tokenising the positive prompt | Set the positive `CLIPTextEncode` node's `text` to `__PROMPT__` |
| Log: "workflow is not valid JSON" | Truncated or mangled paste | Re-paste the full export; check for quoting damage from your SQL client |
| Log: "tool_name … is not a legal MCP tool name" | Spaces or punctuation in `tool_name` | Use `^[a-zA-Z0-9_-]+$` |
| Tool ignores `width`/`height` | The workflow has no `__WIDTH__`/`__HEIGHT__` tokens, so it never advertised them | Tokenise the latent node, or accept the baked size |
| Same prompt always returns the identical image | The workflow has no `__SEED__` token | Set the sampler's seed to `__SEED__` |
| "ComfyUI reported node errors" | Malformed workflow, or the checkpoint named in `ckpt_name` is not installed on the instance | Check the raw error body; verify the model file exists on the ComfyUI host |
| "did not finish within 180s" | ComfyUI is queued behind other jobs, or the GPU is wedged | Look up the prompt id (it is in the message) in the ComfyUI queue |
| 403 Forbidden | Missing `ROLE_MCP-GENERATE-IMAGE` | Add the `mcp-generate-image` role in the IdP |
| Startup fails on the datasource | Postgres unreachable | Verify `DATABASE_URL`; an unreachable database is fatal, unlike a bad row |
| Connection failures within 30s | ComfyUI unreachable | `curl https://<comfyui-host>/system_stats` from the server host |

Operational notes
- The poll loop blocks a request thread for the duration of the generation. That is acceptable at 5–15 seconds and is bounded by `comfyui.generation.timeout-seconds`, but it is the constraint that would push toward async task handoff if a slower model (e.g. FLUX.1-dev) were adopted.
- A 1024×1024 PNG is roughly 2MB once base64-encoded. The ComfyUI WebClient raises its in-memory codec limit to 32MB so there is headroom if larger sizes are ever enabled.
- Workflows are read from the database **once at startup**. Editing a row has no effect until the server restarts.
- Workflow tools are built as `SyncToolSpecification`s rather than `@McpTool` methods, so `@PreAuthorize` cannot sit on the handler — a lambda is not a bean method and would not be proxied. Authorization is enforced by routing every call through `WorkflowImageGenerationService`. Calling `ComfyUiService` directly from a handler would silently bypass the role check.
- The ComfyUI origin has no authentication of its own. `@PreAuthorize` protects this server's surface only — see Deployment for origin hardening.

See also
- Tools: ./tools.md
- Configuration: ./configuration.md
- Deployment: ./deployment.md
- Security: ./security.md
