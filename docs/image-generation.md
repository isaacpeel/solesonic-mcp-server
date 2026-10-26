# Image Generation

Overview
- There is **one** image tool, `generate_image`. A small LangGraph4j graph reads the request, picks the best stored workflow, and refines the prompt; the tool then runs that workflow.
- The workflows themselves are **defined by data**. Each enabled row in the `comfy_workflow` table is one workflow the router can choose, loaded at startup. No workflows ship with the code: every deployment stores its own.
- All of them are backed by a self-hosted [ComfyUI](https://github.com/comfyanonymous/ComfyUI) instance.
- Authorization: callers must have `ROLE_MCP-GENERATE-IMAGE`.
- The image comes back **inline** as MCP `ImageContent` (base64 PNG), not as a URL. Nothing is stored server-side.
- If the table is empty the tool is still listed, but every call fails because the router has nothing to choose from. The rest of the server starts normally.
- A row's `tool_name` is the router's identifier for it; rows are not MCP tools of their own.

Tool shape

`generate_image`
- Input:
  - `prompt` (string, required) — the user's request **exactly as they wrote it**. The router refines it, so the calling model should not enhance it, restyle it, or choose a style first; doing so refines the prompt twice.
  - `width` (integer, optional) — defaults to `1024`. Ignored by a workflow that has no `__WIDTH__` token, such as one that sizes its output from a reference photo.
  - `height` (integer, optional) — defaults to `1024`. Same rule with `__HEIGHT__`.
  - `referenceImages` (array, optional, at most 3) — `{data, mimeType, description?}` items. `description` is a short account of what the image shows — its subjects, and whether a person is the focus — capped at 500 characters. It is used **only to choose the workflow** and is never added to the prompt. See [Reference images](#reference-images).
- Output: a `CallToolResult` with three content blocks:
  1. `ImageContent` — base64-encoded PNG, `mimeType` `image/png`
  2. `TextContent` — workflow name, size, seed, and elapsed seconds
  3. `TextContent` — `Workflow: <tool_name>. Why: <rationale>`, the router's choice and its one-sentence reason, including any fallback it applied

A blank or missing prompt is rejected with an error result **before** any model or ComfyUI call. So is a request with more than 3 reference images.

The defaults for `width` and `height` live in `ImageGenerationRequest`, in code — the table holds no tuning values at all.

How routing works

```
START → classifyImageIntent → selectImageWorkflow → resolveImageWorkflow → writeImagePrompt → END
```

Choosing a workflow and writing its prompt are separate steps, and the choice itself is split into a narrow intent question followed by a lookup. A decision model answers "is this an edit of the person in the photo?" far more reliably than it compares several prose descriptions.

The two choices — intent and workflow — go to a **decision model** (System One protocol, through the LiteLLM proxy; see ./configuration.md). Each is one `choice` question whose options are the only allowed answers, so the model cannot invent a label. The answer carries a confidence: one below `solesonic.image.decision-confidence-threshold` counts as undecided. Only the prompt is written by a chat model.

Every routing call's state leads with the photo facts, for example:

```
[1 photo attached]
Photo 1: a man with glasses and a beard, head and shoulders
Request: put me on a sunny beach holding a cocktail
```

1. **Classify intent (decision model, only when photos are attached).** Without a photo the intent is `NEW_IMAGE` and no model is asked. With a photo, the model is offered exactly two options, `EDIT_PERSON_IN_PHOTO` (keep the person, change where they are or what they are doing) and `RESTYLE_PHOTO` (keep the composition, change the look), and decides from the request and each image's `description`; `prompt/image/classify_image_intent.st` carries the rules and worked examples as the question's instructions. A failure, a missing answer or a confidence below the threshold leaves the intent undecided.
2. **Select workflow (decision model).** Candidates are the workflows with a graph for the attached number of images, narrowed to those that serve the intent (their `comfy_workflow_intent` rows). An undecided intent, or one no such workflow serves, keeps every workflow that can take the images. **One candidate is chosen without a model call**; several are put to the model as options labelled by tool name and described by each row's name and `description`, with guidance for the intent as the instructions. A failure or a confidence below the threshold leaves the workflow unchosen.
3. **Resolve (deterministic).** A missing or unknown pick falls back to the first candidate, so a request that any workflow can take always gets one. No candidates at all fails the call: `No image workflow accepts N reference image(s).`
4. **Write the prompt (LLM).** Given the chosen workflow's name and description, the intent, and the request — **never the image descriptions** — the model writes the final prompt (`prompt/image/write_image_prompt.st`): a full scene for a new image, the new look for a restyle, a short edit instruction that does not describe the person for a person edit. This is the **only** place the prompt is refined. If it fails, the caller's prompt is used as written.
5. **Generate.** The tool runs the chosen workflow with the final prompt. Generation happens after the graph ends, because graph state is checkpointed to Redis and a request's images can be tens of megabytes; the graph sees only their count and descriptions.

Model failures never fail the call: each step falls back as described, and every fallback is appended to the rationale in the result's last text block. A request therefore costs up to three model calls — intent (photos only), selection (several candidates only) and prompt writing — before generation starts.

Intent and workflow selection run on the decision model (`spring.ai.typesafe.model`); prompt writing runs on the image chat model (`solesonic.image.model`) through its own `ChatClient`. Each step logs at INFO: the photo count, candidates, the intent and choice with their confidence and timings, the full probabilities of any choice rejected for low confidence, the final prompt, and any fallback.

Because selection and prompt writing read the workflow descriptions, a description should say **when** its workflow is the right choice, and should not carry instructions about refining the prompt — that guidance lives in the templates.

Substitution tokens

A stored workflow is a plain ComfyUI API-format export carrying literal placeholder strings where caller-supplied values belong. Nothing else about the document is special: node ids, titles and layout are irrelevant.

| Token | Required | Replaced with | JSON type written |
|---|---|---|---|
| `__PROMPT__` | yes | the router's final prompt | string |
| `__SEED__` | no | a fresh random seed per call | number |
| `__WIDTH__` | no | the `width` argument, or `1024` | number |
| `__HEIGHT__` | no | the `height` argument, or `1024` | number |
| `__REFERENCE_IMAGE_1__` … `__REFERENCE_IMAGE_3__` | in reference variants only | the uploaded image's ComfyUI file name | string |

Tokens are replaced **wherever they appear**, with correctly typed JSON — a seed lands as a number, never as the string `"__SEED__"`, so ComfyUI receives exactly what a hand-edited workflow would have produced.

A workflow that pins its own resolution simply omits `__WIDTH__` and `__HEIGHT__`; the `width` and `height` arguments are then ignored for that workflow.

Everything not tokenised is left exactly as stored. That is what makes a row a preset rather than a set of knobs:

| Baked into the stored JSON | Notes |
|---|---|
| `steps` | whatever the model is designed for |
| `cfg` | likewise; distilled models typically expect `1` |
| `sampler_name`, `scheduler`, `denoise` | the workflow author's choice |
| `batch_size` | `1` — only the first image produced is returned |
| negative prompt | inert at `cfg=1`, meaningful above it |
| model loader inputs (`ckpt_name`, `unet_name`, …) | the model itself — effectively what defines the row |
| `filename_prefix` | output naming on the ComfyUI host |

Two models, or one model at two step counts, are **two rows** rather than one row with more parameters.

Seed
- A workflow containing `__SEED__` draws a fresh random seed on every call, so repeating the same prompt gives a different image.
- The seed used is reported in the text block for traceability — it identifies the run in ComfyUI's history — but it is not a caller-supplied knob, so a specific image cannot be regenerated through the tool.
- Omitting `__SEED__` makes a workflow deterministic: every call with the same prompt returns the identical image, and ComfyUI's execution cache serves the repeats.

Why there is no negative prompt or guidance scale parameter
- `cfg` and the negative prompt belong to the workflow, so neither is tokenised or exposed to callers.
- At `cfg` `1` there is no classifier-free guidance and negative conditioning has no effect; a workflow for a model that uses guidance bakes a higher `cfg` and, if it wants one, a fixed negative prompt.

Adding a workflow

Rows are inserted by hand; there is no seeding migration and no admin endpoint.

1. Build the workflow in the ComfyUI editor.
2. Use **Export (API)** — not plain Export. The two formats differ and only the API one is accepted by `POST /prompt`.
3. Replace the values you want callers to control with tokens: set the positive prompt's text to `__PROMPT__`, and optionally the sampler's seed to `__SEED__` and the latent node's dimensions to `__WIDTH__` / `__HEIGHT__`.
4. Do **not** wrap the graph in a `{"prompt": ...}` envelope; Java builds that envelope.
5. Remove preview nodes (`PreviewImage`): the service returns the first image any output node produced, so keep a single `SaveImage`.
6. Insert the row and restart the server. The router offers it from then on.

```sql
INSERT INTO comfy_workflow (name, tool_name, description, workflow_json) VALUES (
    'My workflow',
    'my_workflow',
    'Creates a brand new image from a natural-language description. '
    'Use for general requests; it cannot edit an existing image.',
    '<paste the tokenised API-format export here>'
);
```

Tag the row with the intents it serves — `NEW_IMAGE`, `RESTYLE_PHOTO`, `EDIT_PERSON_IN_PHOTO` — so the router offers it for those requests. A row with no intents is offered only when no workflow that can take the request serves its intent.

```sql
INSERT INTO comfy_workflow_intent (comfy_workflow_id, intent)
SELECT id, 'NEW_IMAGE' FROM comfy_workflow WHERE tool_name = 'my_workflow';
```

`tool_name` must match `^[a-zA-Z0-9_-]+$` and is unique. It is how the router names the workflow, so treat it as an identifier.

<a id="reference-images"></a>Reference images

Any row can optionally accept 1–3 caller-supplied images that guide the result. Without images a workflow runs its text-only graph.

A single ComfyUI graph cannot be both text-to-image and image-guided — a `LoadImage` node with no image fails validation, and image guidance means different wiring, not one extra input. A graph also loads a fixed number of images. Each row therefore carries a text-only graph plus **one image-guided graph per number of images it accepts**:

| Where | Used when |
|---|---|
| `comfy_workflow.workflow_json` | no images supplied |
| a `comfy_workflow_reference_variant` row with `image_count` N (at most one per row and count, N = 1–3) | exactly N images supplied |

A row opts in by adding variant rows. When images are attached, the router only offers rows with a variant for exactly that many, and the call runs that variant.

```sql
INSERT INTO comfy_workflow_reference_variant (comfy_workflow_id, image_count, workflow_json)
SELECT id, 1, '<paste the tokenised API-format export here>'
FROM comfy_workflow WHERE tool_name = 'my_workflow';
```

A variant carries one `__REFERENCE_IMAGE_n__` token per image, as the `image` input of a `LoadImage` node, numbered from 1. All the other tokens work in a variant too, and `__PROMPT__` is required there as well.

Two common wirings:
- **Restyle (img2img):** `LoadImage → (resize) → VAEEncode → KSampler.latent_image` with `denoise` below `1`. The sampler starts from the photo, so the composition stays and the prompt changes the look.
- **Reference conditioning:** an edit model receives the photo as conditioning and the sampler runs at `denoise` `1`, so identity carries over while the setting, pose or clothing change. If the latent is encoded from the photo, the output follows the photo's aspect ratio.

The argument's shape:

```
referenceImages: array, at most 3
  items: { data: string (base64; a data: URI prefix is tolerated),
           mimeType: "image/png" | "image/jpeg" | "image/webp",
           description?: string (what the image shows; routing only, never the prompt) }
```

- A graph is fixed, so a variant with N slots takes **exactly N** images. A workflow accepts every count it has a variant for, so one row can take, say, one photo or two. The router never picks a workflow without a variant for the count; if none has one, the call fails with `No image workflow accepts N reference image(s).` Calling a workflow directly with an unsupported count fails with a message listing the counts it takes, for example `This workflow takes 1 or 2 reference images; 3 supplied.`
- Order matters: image 1 fills `__REFERENCE_IMAGE_1__`.
- Write the row's `description` so it says what the images are *for* ("restyles the attached photo", "keeps the same person while changing the background, pose or setting"), because the router picks the workflow — and writes its prompt — from that text. When several rows accept images, each description should make clear when it is the right one.

Validation, per image, before anything else happens:
- at most 3 images; each must be an object with `data` and `mimeType`;
- `mimeType` on the png/jpeg/webp allow-list, **and** the decoded bytes must carry that format's magic number — the declared type is not trusted;
- decoded size at most `comfyui.reference-image.max-bytes` (default `10MB`), checked from the encoded length before decoding;
- base64 only. There is no URL input, so there is no SSRF surface.

Errors come back as `isError` results whose text names the image's position and the problem, never the data. Neither the image data nor the raw `arguments` map is logged. The images never enter the routing graph's checkpointed state: the graph sees only their count.

ComfyUI mechanics: a `LoadImage` node takes a file name already in ComfyUI's `input/` directory, never bytes. When images are supplied each one is uploaded first with `POST /upload/image` (multipart: `image`, `type=input`, `subfolder=mcp-server`, `overwrite=false`), and the name ComfyUI returns is what gets substituted.

Prompting with a reference photo: the router writes an edit instruction for a person edit — it refers to the person by photo ("the man in image 1") rather than re-describing them, says what changes, then what must stay the same. The caller just passes the request through:

```
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "generate_image",
    "arguments": {
      "prompt": "put me on a tropical beach at sunset, walking along the shore looking over my shoulder",
      "referenceImages": [ {
        "data": "iVBORw0KGgo…",
        "mimeType": "image/png",
        "description": "a man with glasses and a beard, head and shoulders"
      } ]
    }
  }
}
```

The result text reports the requested size (default 1024×1024), not the size a photo-shaped workflow actually produced.

Validation and startup behaviour

Each row is parsed and validated independently at startup. A row is **skipped with a warning** — not fatal — if it:
- is not valid JSON,
- is not a JSON object,
- contains no `__PROMPT__` token,
- has a `tool_name` that is not a legal identifier, or
- has a `workflow_json` that carries reference image tokens — that graph could never run on the no-image path.

Each reference variant is validated separately from its row and from the row's other variants. A variant that is not valid JSON, has no `__PROMPT__`, has no reference token, has slots that are not contiguous from 1, has more than 3 slots, or loads a different number of images than its `image_count` is **dropped with a warning while the row's text-only workflow and its other variants still load**.

Rows are typed in by hand, so one malformed paste must not be able to stop the server booting and take every unrelated tool down with it. Check the startup log to confirm what actually loaded:

```
INFO  Registering ComfyUI workflow tool 'my_workflow' (My workflow)
INFO  ComfyUI workflow tool 'my_workflow' accepts 1, 2 reference image(s)
INFO  Loaded 1 ComfyUI workflow(s) available to the image router: [my_workflow]
WARN  Ignoring the 3-image reference variant of ComfyUI workflow 'my_workflow': its graph loads 2 image(s)
```

This differs from the rest of the configuration surface, which fails fast. An unreachable *database* still fails startup — it is only a bad row or variant that is tolerated.

The ComfyUI contract
The service speaks ComfyUI's native protocol:

1. `GET /ws?clientId=<id>` — opens ComfyUI's event stream under a fresh client id for each generation. ComfyUI only sends a prompt's events to a socket already registered under that prompt's `client_id` and buffers nothing, so the service waits for the `status` frame ComfyUI sends on connect (up to `comfyui.events.connect-timeout-millis`) before submitting.
2. `POST /prompt` — submits the patched API-format workflow with the same `client_id`.
   ComfyUI answers **HTTP 200 with a populated `node_errors` object** when the workflow is malformed, so the service checks `node_errors` explicitly rather than trusting the status code.
3. Wait for completion on the event stream. `executing` with a null node means the job is done and its history entry is written. `execution_error` fails the call with the failing node's type and exception message; `execution_interrupted` fails it naming the node.
4. `GET /history/{promptId}` — the source of the output image reference. While the job is pending or running the response is `{}`; `status.status_str == "error"` fails the call. It is also checked whenever no event has arrived for `comfyui.generation.poll-interval-millis`, so a missed frame delays the result by one interval rather than hanging until the deadline.
5. `GET /view?filename=&subfolder=&type=` — downloads the rendered PNG, which is then base64-encoded.

If the event stream cannot be opened (ComfyUI unreachable, a proxy that does not pass WebSocket upgrades, the connect timeout) or closes mid-run, the service logs a warning and polls `/history` every `comfyui.generation.poll-interval-millis` instead. The generation still completes; only the progress detail is lost.

Every individual HTTP call is short (`comfyui.api.response-timeout-seconds`, default 30s). The long wait is bounded separately by `comfyui.generation.timeout-seconds`, measured from the start of the generation, reference image uploads included.

The `SaveImage` node is not bound — the service locates the output image by scanning the history entry's outputs for the first node that produced images.

Progress
- The tool emits MCP progress notifications: choosing a workflow (5%), the chosen workflow (15%), queued with the prompt id, the generation itself (15–85%), downloading (90%), and a final 100% naming the size, elapsed time, and seed. Progress never moves backwards, so a generation step below 15% is held at 15%.
- The generation range comes from ComfyUI's events, not an estimate. The percentage is the share of the workflow's nodes that have run — `(finished nodes + the running node's step fraction) / nodes to run` — where nodes served from ComfyUI's cache are left out. Messages name what ComfyUI is doing:

| Stage | Message |
|---|---|
| Waiting behind other prompts | `Waiting in queue (N ahead)` |
| A node starts | `Running <node title>` |
| A node reports steps (samplers) | `<node title>: step 12/30` |
| All nodes ran | `Finishing` |

- Node titles are the workflow's `_meta.title` values (the names shown in the ComfyUI editor), falling back to `class_type`.
- Nodes take very different amounts of time, so the bar pauses on a slow node with no step events, such as a model load, and moves with each sampler step. The node count is approximate: ComfyUI skips nodes no output depends on, so a run can end below 85% and jump to 90%.
- Without the event stream the message is `Generating…` and the percentage holds where it was.

Timing: generation time is a property of each workflow — model size, step count, `cfg` above 1 (two model passes per step), extra passes such as face detailing, and the number of reference images. Size `comfyui.generation.timeout-seconds` from the slowest workflow's measured time on your ComfyUI host, and keep `spring.mvc.async.request-timeout` above it.

Configuration

| Property | Default | Purpose |
|---|---|---|
| `comfyui.api.uri` | `${COMFYUI_API_URI}` | Base URI of the ComfyUI instance |
| `comfyui.api.response-timeout-seconds` | `30` | Timeout for each individual HTTP call |
| `comfyui.generation.timeout-seconds` | `180` | Deadline for a whole generation |
| `comfyui.generation.poll-interval-millis` | `1000` | Quiet time on the event stream before a `/history` check; the poll interval when the stream is unavailable |
| `comfyui.events.connect-timeout-millis` | `5000` | How long to wait for the `/ws` event stream to register before falling back to polling |
| `comfyui.generation.release-memory-delay-millis` | `300000` | Delay before calling `/free` after the last generation |
| `comfyui.reference-image.max-bytes` | `10MB` | Largest decoded size accepted per reference image |
| `spring.datasource.url` | `${DATABASE_URL}` | Postgres holding `comfy_workflow` and `comfy_workflow_reference_variant` |

Environment variables
- `COMFYUI_API_URI` — e.g. `https://comfyui.example.com`
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`

Examples (MCP JSON-RPC)

```
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "generate_image",
    "arguments": { "prompt": "a lighthouse on a cliff in a storm" }
  }
}
```

With an explicit size, which workflows that tokenise their dimensions honour:

```
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "generate_image",
    "arguments": { "prompt": "a portrait of a fox in a library", "width": 832, "height": 1216 }
  }
}
```

Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Every call fails with "No image workflow accepts 0 reference image(s)" | Table empty, or every row was skipped | Check the startup log for `Skipping ComfyUI workflow` warnings |
| Result says the first candidate was used | The decision model failed, or its choice fell below the confidence threshold | Check the log for `The decision model failed` or `rejected ... confidence ... is below`; check the decision model is reachable at `CHAT_OPENAI_HOST`, and rewrite the rows' `description` text if choices are consistently low-confidence |
| A photo edit of a person runs the restyle workflow | The intent was classified as `RESTYLE_PHOTO`, often because the client put its own description of the photo into `prompt` instead of `description` | Check the `Image intent classified as` log line; have the client pass the user's words in `prompt` and the photo description in `description` |
| The router keeps picking the wrong workflow | The rows' intents or descriptions do not say clearly when each one applies | Check each row's `comfy_workflow_intent` entries, rewrite the rows' `description` text, then restart |
| Log: "workflow has no `__PROMPT__` token" | The export was pasted without tokenising the positive prompt | Set the positive prompt node's text to `__PROMPT__` |
| Log: "workflow is not valid JSON" | Truncated or mangled paste | Re-paste the full export; check for quoting damage from your SQL client |
| Log: "tool_name … is not a legal MCP tool name" | Spaces or punctuation in `tool_name` | Use `^[a-zA-Z0-9_-]+$` |
| `width`/`height` have no effect | The chosen workflow has no `__WIDTH__`/`__HEIGHT__` tokens | Tokenise the latent node, or accept the baked size |
| Same prompt always returns the identical image | The workflow has no `__SEED__` token | Set the sampler's seed to `__SEED__` |
| "ComfyUI reported node errors" or "ComfyUI rejected the workflow with status 400" | Malformed workflow, a model file or custom node the workflow uses is not installed on the instance, or a ComfyUI upgrade changed a node's inputs | The ComfyUI console names the node and value (`Value not in list: …`); install the file or node, or re-export the workflow from the current ComfyUI |
| "did not finish within Ns" | The workflow is slower than `comfyui.generation.timeout-seconds`, ComfyUI is queued behind other jobs, or the GPU is wedged | Look up the prompt id (it is in the message) in the ComfyUI queue; raise the timeout if the workflow is simply slow |
| 403 Forbidden | Missing `ROLE_MCP-GENERATE-IMAGE` | Add the `mcp-generate-image` role in the IdP |
| Startup fails on the datasource | Postgres unreachable | Verify `DATABASE_URL`; an unreachable database is fatal, unlike a bad row |
| Connection failures within 30s | ComfyUI unreachable | `curl https://<comfyui-host>/system_stats` from the server host |
| "No image workflow accepts N reference image(s)" with images attached | No row has a variant for that many images, or the variant was dropped | Check the startup log for `Ignoring the N-image reference variant` and the `accepts … reference image(s)` lines |
| "content does not match its declared type" | The bytes are not the format `mimeType` claims | Send the real type; GIF is not accepted |
| "ComfyUI image upload failed with status …" | ComfyUI could not decode or store the file | Check the ComfyUI log; check disk space in `input/` |

Operational notes
- After each generation, the service schedules a call to `/free` (unloading the model and releasing VRAM) `comfyui.generation.release-memory-delay-millis` later. A generation that lands before that delay elapses cancels and reschedules it, so `/free` fires at most once — always that delay after the *last* generation — instead of forcing a reload between back-to-back requests.
- The ComfyUI origin's proxy must pass WebSocket upgrades on `/ws` (`Upgrade` and `Connection` headers) for step progress; without them generation still works, with the `Generating…` fallback and a warning in the log for each generation.
- The wait blocks a request thread for the duration of the generation, bounded by `comfyui.generation.timeout-seconds`. Slow workflows hold the thread for longer; if they become frequent or slow enough to approach the timeout, this is the constraint that pushes toward async task handoff.
- Routing adds up to two decision-model calls and one chat-model call before generation starts.
- A 1024×1024 PNG is roughly 2MB once base64-encoded. The ComfyUI WebClient raises its in-memory codec limit to 32MB so there is headroom for larger sizes.
- Workflows are read from the database **once at startup**. Editing a row or variant has no effect until the server restarts.
- Authorization is checked twice: `@PreAuthorize` on the `generate_image` method, and again on `WorkflowImageGenerationService`, which is also where reference images are decoded. Calling `ComfyUiService` directly would bypass the role check.
- Reference image uploads accumulate in ComfyUI's `input/mcp-server/` directory: ComfyUI has no API to delete an input. Every file is named `mcp-<uuid>.<ext>` so the directory can be pruned by age (e.g. a daily `find input/mcp-server -mtime +1 -delete`) without touching anything else.
- A request carrying three 10MB images is roughly 40MB of JSON. Tomcat places no limit on a JSON body, but a reverse proxy in front of this server (`client_max_body_size` in nginx) must allow it.
- The ComfyUI origin has no authentication of its own. `@PreAuthorize` protects this server's surface only — see Deployment for origin hardening.

See also
- Tools: ./tools.md
- Configuration: ./configuration.md
- Deployment: ./deployment.md
- Security: ./security.md
