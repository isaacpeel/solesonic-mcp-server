# Image Generation

Overview
- There is **one** image tool, `generate_image`. A small LangGraph4j graph reads the request, picks the best stored workflow, and refines the prompt; the tool then runs that workflow.
- The workflows themselves are **defined by data**. Each enabled row in the `comfy_workflow` table is one workflow the router can choose, loaded at startup.
- All of them are backed by a self-hosted [ComfyUI](https://github.com/comfyanonymous/ComfyUI) instance.
- Authorization: callers must have `ROLE_MCP-GENERATE-IMAGE`.
- The image comes back **inline** as MCP `ImageContent` (base64 PNG), not as a URL. Nothing is stored server-side.
- If the table is empty the tool is still listed, but every call fails because the router has nothing to choose from. The rest of the server starts normally.

> **Breaking change:** each `comfy_workflow` row is no longer its own MCP tool, and the `_meta: {"solesonic/kind": "image-generation"}` tag is gone. A client finds the single image tool by its name, `generate_image`, which now routes to whichever row fits — including, but not limited to, the row whose `tool_name` is also `generate_image`. The reference-image argument is now `referenceImages` (previously `reference_images`), and each image may carry a `description`. A row's `tool_name` is now only the router's identifier for it.

Tool shape

`generate_image`
- Input:
  - `prompt` (string, required) — the user's request **exactly as they wrote it**. The router refines it, so the calling model should not enhance it, restyle it, or choose a style first; doing so refines the prompt twice.
  - `width` (integer, optional) — defaults to `1024`. Ignored by a workflow that has no `__WIDTH__` token, such as the person-reference workflow, which keeps the photo's aspect ratio.
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

Choosing a workflow and writing its prompt are separate steps, and the choice itself is split into a narrow intent question followed by a lookup. A small routing model answers "is this an edit of the person in the photo?" far more reliably than it compares several prose descriptions.

Every routing call's user message leads with the photo facts, for example:

```
[1 photo attached]
Photo 1: a man with glasses and a beard, head and shoulders
Request: put me on a sunny beach holding a cocktail
```

1. **Classify intent (LLM, only when photos are attached).** Without a photo the intent is `NEW_IMAGE` and no model is asked. With a photo, the model chooses `EDIT_PERSON_IN_PHOTO` (keep the person, change where they are or what they are doing) or `RESTYLE_PHOTO` (keep the composition, change the look), using the request and each image's `description`; `prompt/image/classify_image_intent.st` carries the rules and worked examples. An unusable answer — a failure, nothing parseable, or `NEW_IMAGE` despite a photo — leaves the intent undecided.
2. **Select workflow.** Candidates are the workflows that accept exactly the attached number of images, narrowed to those configured for the intent (`solesonic.image.intent-workflows.*`). An undecided or unmapped intent, or one whose workflows cannot take the images, keeps every workflow that can. **One candidate is chosen without a model call**; several are put to the model (`prompt/image/select_image_workflow.st`), which picks by description.
3. **Resolve (deterministic).** A missing or unknown pick falls back to `solesonic.image.default-workflow`, or to the first candidate when the default is not among them. No candidates at all fails the call: `No image workflow accepts N reference image(s).`
4. **Write the prompt (LLM).** Given the chosen workflow's name and description, the intent, and the request — **never the image descriptions** — the model writes the final prompt (`prompt/image/write_image_prompt.st`): a full scene for a new image, the new look for a restyle, a short edit instruction that does not describe the person for a person edit. This is the **only** place the prompt is refined. If it fails, the caller's prompt is used as written.
5. **Generate.** The tool runs the chosen workflow with the final prompt. Generation happens after the graph ends, because graph state is checkpointed to Redis and a request's images can be tens of megabytes; the graph sees only their count and descriptions.

Model failures never fail the call: each step falls back as described, and every fallback is appended to the rationale in the result's last text block. A request therefore costs up to three model calls — intent (photos only), selection (several candidates only) and prompt writing — before generation starts.

The router runs on the agile model (`AgileChatClientConfig.AGILE_MODEL`) through its own `ChatClient`. Each step logs at INFO: the photo count, candidates, the intent and choice with timings, the final prompt, and any fallback.

Because selection and prompt writing read the workflow descriptions, a description should say **when** its workflow is the right choice, and should not carry instructions about refining the prompt — that guidance lives in the templates. `scripts/comfy-workflow-remove-prompt-enhancement.sql` removes the old "Mandatory Prompt Enhancement" section from the `generate_image` row; restart afterwards.

Substitution tokens

A stored workflow is a plain ComfyUI API-format export carrying literal placeholder strings where caller-supplied values belong. Nothing else about the document is special: node ids, titles and layout are irrelevant.

| Token | Required | Replaced with | JSON type written |
|---|---|---|---|
| `__PROMPT__` | yes | the router's final prompt | string |
| `__SEED__` | no | a fresh random seed per call | number |
| `__WIDTH__` | no | the `width` argument, or `1024` | number |
| `__HEIGHT__` | no | the `height` argument, or `1024` | number |

Tokens are replaced **wherever they appear**, with correctly typed JSON — a seed lands as a number, never as the string `"__SEED__"`, so ComfyUI receives exactly what a hand-edited workflow would have produced.

A workflow that pins its own resolution simply omits `__WIDTH__` and `__HEIGHT__`; the `width` and `height` arguments are then ignored for that workflow.

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
5. Insert the row and restart the server. The router offers it from then on.

```sql
INSERT INTO comfy_workflow (name, tool_name, description, workflow_json) VALUES (
    'FLUX.1-schnell',
    'generate_image_flux_schnell',
    'Creates a brand new image from a natural-language description. '
    'Fast photoreal and illustrative generation at 1024x1024, roughly 5-15 seconds. '
    'Use for general requests; it cannot edit, resize, or annotate an existing image.',
    '<paste the tokenised API-format export here>'
);
```

No sample workflow files ship in the repository. The database is the only source of truth: export from your own ComfyUI editor and paste into the row.

`tool_name` must match `^[a-zA-Z0-9_-]+$` and is unique. It is how the router names the workflow, and the value `solesonic.image.default-workflow` refers to, so treat it as an identifier.

Reference images

Any row can optionally accept 1–3 caller-supplied images that guide the result. Without images a workflow behaves exactly as before. The shipped rows use the images in two different ways:

| Workflow | Reference graph | Effect |
|---|---|---|
| `generate_image`, `generate_special_image` | FLUX.1-schnell img2img | same composition, restyled by the prompt |
| `person_reference_image` | FLUX.1 Kontext-dev | same person, new background, setting or pose — see [Person reference](#person-reference) |

A single ComfyUI graph cannot be both text-to-image and image-guided — a `LoadImage` node with no image fails validation, and image guidance means different wiring (a `VAEEncode` feeding the sampler, or `ReferenceLatent` conditioning), not one extra input. Each row therefore carries **two graphs**:

| Column | Used when |
|---|---|
| `workflow_json` | no images supplied (unchanged) |
| `reference_workflow_json` (nullable, `V2` migration) | images supplied |

A row opts in by filling `reference_workflow_json`. When images are attached, the router only offers rows whose variant takes exactly that many.

The variant carries one token per image, as the `image` input of a `LoadImage` node:

| Token | Replaced with |
|---|---|
| `__REFERENCE_IMAGE_1__` … `__REFERENCE_IMAGE_3__` | the uploaded file's name (string), e.g. `mcp-server/mcp-<uuid>.png` |

All the other tokens work in the variant too, and `__PROMPT__` is required there as well.

The argument's shape:

```
referenceImages: array, at most 3
  items: { data: string (base64; a data: URI prefix is tolerated),
           mimeType: "image/png" | "image/jpeg" | "image/webp",
           description?: string (what the image shows; routing only, never the prompt) }
```

- A graph is fixed, so a variant with N slots takes **exactly N** images, or none. The router never picks a workflow that would reject the count; if none accepts it, the call fails with `No image workflow accepts N reference image(s).`
- Order matters: image 1 fills `__REFERENCE_IMAGE_1__`.
- Write the row's `description` so it says what the images are *for* ("restyles the attached photo", "keeps the same person while changing the background, pose or setting"), because the router picks the workflow — and writes its prompt — from that text. When several rows accept images, each description should make clear when it is the right one.

Validation, per image, before anything else happens:
- at most 3 images; each must be an object with `data` and `mimeType`;
- `mimeType` on the png/jpeg/webp allow-list, **and** the decoded bytes must carry that format's magic number — the declared type is not trusted;
- decoded size at most `comfyui.reference-image.max-bytes` (default `10MB`), checked from the encoded length before decoding;
- base64 only. There is no URL input, so there is no SSRF surface.

Errors come back as `isError` results whose text names the image's position and the problem, never the data. Neither the image data nor the raw `arguments` map is logged. The images never enter the routing graph's checkpointed state: the graph sees only their count.

Startup validation of a variant is separate from its row: a variant that is not valid JSON, has no `__PROMPT__`, has no reference token, has slots that are not contiguous from 1, or has more than 3 slots is **dropped with a warning while the row's text-only workflow still loads**. Conversely, a `workflow_json` that carries reference tokens skips the whole row — that graph could never run on the no-image path.

ComfyUI mechanics: a `LoadImage` node takes a file name already in ComfyUI's `input/` directory, never bytes. When images are supplied each one is uploaded first with `POST /upload/image` (multipart: `image`, `type=input`, `subfolder=mcp-server`, `overwrite=false`), and the name ComfyUI returns is what gets substituted.

Restyle variant (`generate_image`, `generate_special_image`; one slot):
- Graph: `LoadImage(__REFERENCE_IMAGE_1__) → ImageScale(__WIDTH__, __HEIGHT__) → VAEEncode → KSampler.latent_image`, `denoise` baked at `0.6`. The sampler starts from the photo, so the composition stays and the prompt restyles it.
- `generate_special_image` keeps its three-LoRA chain and 7 steps on this path.
- `scripts/comfy-workflow-set-reference.sql` and `scripts/comfy-workflow-set-special-reference.sql` originally set these variants. `scripts/comfy-workflow-revert-kontext-reference.sql` restores them, and the original description text, on a database where the Kontext experiment was applied to these rows.

<a id="person-reference"></a>Person reference (`person_reference_image`; one slot):
- Purpose: edit a photo of a person while keeping them recognisably the same — face, hair, skin tone — while the prompt changes the background, setting, pose, clothing or lighting.
- Reference graph: FLUX.1 Kontext-dev, as tested in ComfyUI (`flux_kontext_reference_baseline`, the "Flux Kontext Dev Image Edit" template). `LoadImage(__REFERENCE_IMAGE_1__) → ImageStitch → FluxKontextImageScale → VAEEncode`, which feeds both `ReferenceLatent` conditioning and the sampler's latent. `FluxGuidance` `2.5`, `20` steps, `cfg` `1`, `euler` / `simple`, `denoise` `1`; negative is `ConditioningZeroOut`. Models load through `UNETLoader`, `DualCLIPLoader` and `VAELoader`.
- Identity comes from `ReferenceLatent`. At `denoise` `1` the photo's latent is fully re-noised, so it only fixes the output size: **the result keeps the photo's aspect ratio** (scaled by `FluxKontextImageScale` to a Kontext resolution of about one megapixel).
- Text-only graph (`workflow_json`, used when no photo is attached): FLUX.1-schnell at a fixed 1024×1024. It carries no `__WIDTH__` / `__HEIGHT__`, so `width` / `height` are ignored for it.
- Required on the ComfyUI host, under `models/`: `diffusion_models/flux1-dev-kontext_fp8_scaled.safetensors`, `text_encoders/clip_l.safetensors`, `text_encoders/t5xxl_fp8_e4m3fn_scaled.safetensors`, `vae/ae.safetensors`, plus `checkpoints/flux1-schnell-fp8.safetensors` for the text-only fallback. Download links are in the template's "Model links" note.
- `scripts/comfy-workflow-insert-person-reference.sql` creates the row. Restart the server afterwards.

Prompting with a reference photo: Kontext follows edit-style instructions. The prompt does the editing — not the sampler's `denoise` — so the router writes an edit instruction for this workflow rather than re-describing the person. The caller just passes the request through:

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

Timing: `person_reference_image` runs Kontext-dev at 20 steps with a reference photo, so it is noticeably slower than the 4-step schnell paths. The progress ramp (`comfyui.generation.expected-seconds`, default `12`) is tuned for schnell and will under-estimate it; the whole call is still bounded by `comfyui.generation.timeout-seconds` (default `180`). The result text reports the requested size (1024×1024) rather than the photo-shaped size actually produced.

Validation and startup behaviour

Each row is parsed and validated independently at startup. A row is **skipped with a warning** — not fatal — if it:
- is not valid JSON,
- is not a JSON object,
- contains no `__PROMPT__` token, or
- has a `tool_name` that is not a legal identifier.

Rows are typed in by hand, so one malformed paste must not be able to stop the server booting and take every unrelated tool down with it. Check the startup log to confirm what actually loaded:

```
INFO  Registering ComfyUI workflow tool 'generate_image_flux_schnell' (FLUX.1-schnell)
INFO  Loaded 1 ComfyUI workflow(s) available to the image router: [generate_image_flux_schnell]
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
- The tool emits MCP progress notifications: choosing a workflow (5%), the chosen workflow (15%), then the generation's own steps — queued with the prompt id, a time-based ramp capped at 85%, downloading (90%), and a final 100% naming the size, elapsed time, and seed. Progress never moves backwards, so a generation step below 15% is held at 15%.
- The ramp is an estimate derived from `comfyui.generation.expected-seconds`, not real per-step progress. It is capped below completion so it never claims the image is ready early.

Configuration

| Property | Default | Purpose |
|---|---|---|
| `solesonic.image.default-workflow` | `generate_image` | The `tool_name` the router falls back to when its pick is missing or unknown |
| `solesonic.image.intent-workflows.new-image` | `generate_image,generate_special_image` | Workflows offered for a request without a photo |
| `solesonic.image.intent-workflows.restyle-photo` | `generate_image,generate_special_image` | Workflows offered to restyle an attached photo |
| `solesonic.image.intent-workflows.edit-person-in-photo` | `person_reference_image` | Workflows offered to edit the person in an attached photo |
| `comfyui.api.uri` | `${COMFYUI_API_URI}` | Base URI of the ComfyUI instance |
| `comfyui.api.response-timeout-seconds` | `30` | Timeout for each individual HTTP call |
| `comfyui.generation.timeout-seconds` | `180` | Deadline for a whole generation |
| `comfyui.generation.poll-interval-millis` | `1000` | Delay between history polls |
| `comfyui.generation.expected-seconds` | `12` | Drives the progress ramp |
| `comfyui.generation.release-memory-delay-millis` | `300000` | Delay before calling `/free` after the last generation |
| `comfyui.reference-image.max-bytes` | `10MB` | Largest decoded size accepted per reference image |
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
| Result says the default workflow was used | The routing model failed or chose an unknown workflow | Check the log for `The image routing model failed`; check the agile model is reachable |
| A photo edit of a person runs the restyle workflow | The intent was classified as `RESTYLE_PHOTO`, often because the client put its own description of the photo into `prompt` instead of `description` | Check the `Image intent classified as` log line; have the client pass the user's words in `prompt` and the photo description in `description` |
| The router keeps picking the wrong workflow | The intent mapping or the descriptions do not say clearly when each one applies | Check `solesonic.image.intent-workflows.*`, rewrite the rows' `description` text, then restart |
| Log: "workflow has no `__PROMPT__` token" | The export was pasted without tokenising the positive prompt | Set the positive `CLIPTextEncode` node's `text` to `__PROMPT__` |
| Log: "workflow is not valid JSON" | Truncated or mangled paste | Re-paste the full export; check for quoting damage from your SQL client |
| Log: "tool_name … is not a legal MCP tool name" | Spaces or punctuation in `tool_name` | Use `^[a-zA-Z0-9_-]+$` |
| `width`/`height` have no effect | The chosen workflow has no `__WIDTH__`/`__HEIGHT__` tokens | Tokenise the latent node, or accept the baked size |
| Same prompt always returns the identical image | The workflow has no `__SEED__` token | Set the sampler's seed to `__SEED__` |
| "ComfyUI reported node errors" | Malformed workflow, or the checkpoint named in `ckpt_name` is not installed on the instance | Check the raw error body; verify the model file exists on the ComfyUI host |
| "ComfyUI rejected the workflow with status 400" when `person_reference_image` runs with a photo | ComfyUI's prompt validation failed — usually a Kontext model file (`flux1-dev-kontext_fp8_scaled.safetensors`, `clip_l.safetensors`, `t5xxl_fp8_e4m3fn_scaled.safetensors`, `ae.safetensors`) is not installed | The ComfyUI console names the node and file (`Value not in list: …`); install the file under `models/`. The text-only path uses a different model and is unaffected |
| "did not finish within 180s" | ComfyUI is queued behind other jobs, or the GPU is wedged | Look up the prompt id (it is in the message) in the ComfyUI queue |
| 403 Forbidden | Missing `ROLE_MCP-GENERATE-IMAGE` | Add the `mcp-generate-image` role in the IdP |
| Startup fails on the datasource | Postgres unreachable | Verify `DATABASE_URL`; an unreachable database is fatal, unlike a bad row |
| Connection failures within 30s | ComfyUI unreachable | `curl https://<comfyui-host>/system_stats` from the server host |
| "No image workflow accepts N reference image(s)" with images attached | No row's variant takes that many images, or the variant was dropped | Check the startup log for `Ignoring the reference variant` |
| "content does not match its declared type" | The bytes are not the format `mimeType` claims | Send the real type; GIF is not accepted |
| "ComfyUI image upload failed with status …" | ComfyUI could not decode or store the file | Check the ComfyUI log; check disk space in `input/` |

Operational notes
- After each generation, the service schedules a call to `/free` (unloading the model and releasing VRAM) `comfyui.generation.release-memory-delay-millis` later. A generation that lands before that delay elapses cancels and reschedules it, so `/free` fires at most once — always that delay after the *last* generation — instead of forcing a reload between back-to-back requests.
- The poll loop blocks a request thread for the duration of the generation, bounded by `comfyui.generation.timeout-seconds`. That is acceptable at 5–15 seconds for the schnell paths. `person_reference_image` already runs a slower model (FLUX.1 Kontext-dev at 20 steps), so its photo calls hold the thread noticeably longer; if they become frequent or slow enough to approach the timeout, this is the constraint that pushes toward async task handoff.
- Routing adds one call to the agile model before generation starts.
- A 1024×1024 PNG is roughly 2MB once base64-encoded. The ComfyUI WebClient raises its in-memory codec limit to 32MB so there is headroom if larger sizes are ever enabled.
- Workflows are read from the database **once at startup**. Editing a row has no effect until the server restarts.
- Authorization is checked twice: `@PreAuthorize` on the `generate_image` method, and again on `WorkflowImageGenerationService`, which is also where reference images are decoded. Calling `ComfyUiService` directly would bypass the role check.
- Reference image uploads accumulate in ComfyUI's `input/mcp-server/` directory: ComfyUI has no API to delete an input. Every file is named `mcp-<uuid>.<ext>` so the directory can be pruned by age (e.g. a daily `find input/mcp-server -mtime +1 -delete`) without touching anything else.
- A request carrying three 10MB images is roughly 40MB of JSON. Tomcat places no limit on a JSON body, but a reverse proxy in front of this server (`client_max_body_size` in nginx) must allow it.
- The ComfyUI origin has no authentication of its own. `@PreAuthorize` protects this server's surface only — see Deployment for origin hardening.

See also
- Tools: ./tools.md
- Configuration: ./configuration.md
- Deployment: ./deployment.md
- Security: ./security.md
