# Image Generation

Overview
- The server exposes a single image generation tool, `generate_image`, backed by a self-hosted [ComfyUI](https://github.com/comfyanonymous/ComfyUI) instance running FLUX.1-schnell.
- Authorization: callers must have `ROLE_MCP-GENERATE-IMAGE`.
- The image comes back **inline** as MCP `ImageContent` (base64 PNG), not as a URL. Nothing is stored server-side.

Tool and Signature

- generate_image
  - Description: Generates a 1024x1024 image from a text prompt and returns it as a PNG.
  - Auth: ROLE_MCP-GENERATE-IMAGE
  - Input:
    - `prompt` (string, required) — subject, style, lighting, composition. Longer and more specific is better.
  - Output: a `CallToolResult` with two content blocks:
    1. `ImageContent` — base64-encoded PNG, `mimeType` `image/png`
    2. `TextContent` — model, size, steps, seed, and elapsed seconds

Fixed generation parameters
- The prompt is the tool's **only** parameter. Everything else is pinned server-side in `ComfyUiConstants`:

| Parameter | Value | Why it is not exposed |
|---|---|---|
| Size | `1024x1024` | FLUX.1-schnell's native resolution |
| Steps | `4` | schnell is distilled for 4 steps; more is slower without being better |
| Seed | fresh random per call | see below |

- A blank or missing prompt is rejected with an error result **before** any call to ComfyUI.

Seed
- Every call draws a fresh random seed, so repeating the same prompt gives a different image.
- The seed used is reported in the text block for traceability — it identifies the run in ComfyUI's history — but it is not a caller-supplied knob, so a specific image cannot be regenerated through this tool.

Why there is no negative prompt or guidance scale
- The workflow pins `cfg` to `1`. FLUX.1-schnell is a distilled model that expects it; raising it degrades output.
- With `cfg` at 1 there is no classifier-free guidance, so the negative conditioning has no effect on the result. The workflow does contain a negative `CLIPTextEncode` node (id `33`), but it is inert and left with an empty string.
- Exposing `negativePrompt` would therefore be a parameter that silently does nothing, and exposing `guidance_scale` would only be a way to break the model. Both are deliberately absent.

The ComfyUI contract
The service speaks ComfyUI's native three-call protocol:

1. `POST /prompt` — submits the patched API-format workflow with a per-request `client_id`.
   ComfyUI answers **HTTP 200 with a populated `node_errors` object** when the workflow is malformed, so the service checks `node_errors` explicitly rather than trusting the status code.
2. `GET /history/{promptId}` — polled every `comfyui.generation.poll-interval-millis`. While the job is pending or running the response is `{}`; that emptiness is the "not done" signal. `status.status_str == "error"` fails the call.
3. `GET /view?filename=&subfolder=&type=` — downloads the rendered PNG, which is then base64-encoded.

Every individual HTTP call is short (`comfyui.api.response-timeout-seconds`, default 30s). The long wait is the poll loop, bounded separately by `comfyui.generation.timeout-seconds` (default 180s). A wedged instance therefore fails on the first poll rather than tying up a socket.

Progress
- `generate_image` emits MCP progress notifications while it waits: submitting (5%), queued with the prompt id (15%), a time-based ramp capped at 85%, downloading (90%), and a final 100% naming the size, elapsed time, and seed.
- The ramp is an estimate derived from `comfyui.generation.expected-seconds`, not real per-step progress. It is capped below completion so it never claims the image is ready early.

Configuration

| Property | Default | Purpose |
|---|---|---|
| `comfyui.api.uri` | `${COMFYUI_API_URI}` | Base URI of the ComfyUI instance |
| `comfyui.api.response-timeout-seconds` | `30` | Timeout for each individual HTTP call |
| `comfyui.generation.timeout-seconds` | `180` | Deadline for a whole generation |
| `comfyui.generation.poll-interval-millis` | `1000` | Delay between history polls |
| `comfyui.generation.expected-seconds` | `12` | Drives the progress ramp |
| `comfyui.workflow.flux-schnell` | `classpath:comfyui/flux1-schnell.json` | Workflow resource |

Environment variables
- `COMFYUI_API_URI` — e.g. `https://comfy.izzy-bot.com`

The workflow resource — re-export contract

`src/main/resources/comfyui/flux1-schnell.json` is a **ComfyUI API-format export**, loaded and validated once at startup.

To update it:
1. Load the template in the ComfyUI editor.
2. Use **Export (API)** — not plain Export. The two formats differ and only the API one is accepted by `POST /prompt`.
3. Replace the file. Do **not** wrap the graph in a `{"prompt": ...}` envelope; Java builds that envelope.
4. Optionally reset the positive prompt text to a short placeholder — it is overwritten on every request.
5. Copy the same file to `src/test/resources/comfyui/flux1-schnell-test.json`.

**Nodes are bound by `_meta.title`, never by node id.** Node ids are assigned by the editor and shift on every re-export. The graph contains **two** `CLIPTextEncode` nodes, so an id shift that landed the prompt on the negative node would generate the wrong image successfully, with no error anywhere. The bound titles are:

| `_meta.title` | Required `class_type` | Patched inputs |
|---|---|---|
| `CLIP Text Encode (Positive Prompt)` | `CLIPTextEncode` | `text` |
| `KSampler` | `KSampler` | `seed` (or `noise_seed`), `steps` |
| `EmptySD3LatentImage` | `EmptySD3LatentImage` | `width`, `height` |

If a title is missing, or a title resolves to a node of the wrong `class_type`, the application **fails at startup** with a message naming the title. That is deliberate: discovering a broken binding on the first tool call in production is the worst possible time to learn the workflow was re-exported carelessly.

The `SaveImage` node is not bound — the service locates the output image by scanning the history entry's outputs for the first node that produced images.

Examples (MCP JSON-RPC)

```
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "generate_image",
    "arguments": { "prompt": "a lighthouse on a cliff in a storm, dramatic lighting, photorealistic" }
  }
}
```

Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails with "ComfyUI workflow has no node titled …" | The workflow was re-exported and a bound node's title changed | Restore the stock title, or update the constant in `ComfyUiConstants` |
| Startup fails with "has class_type … expected …" | A title is bound to the wrong node kind | Check the export; confirm it is the FLUX.1-schnell template |
| "ComfyUI reported node errors" | Malformed workflow, or the checkpoint named in `ckpt_name` is not installed on the instance | Check the raw error body; verify the model file exists on the ComfyUI host |
| "did not finish within 180s" | ComfyUI is queued behind other jobs, or the GPU is wedged | Look up the prompt id (it is in the message) in the ComfyUI queue |
| 403 Forbidden | Missing `ROLE_MCP-GENERATE-IMAGE` | Add the `mcp-generate-image` role in the IdP |
| Connection failures within 30s | ComfyUI unreachable | `curl https://<comfyui-host>/system_stats` from the server host |

Operational notes
- The poll loop blocks a request thread for the duration of the generation. That is acceptable at 5–15 seconds and is bounded by `comfyui.generation.timeout-seconds`, but it is the constraint that would push toward async task handoff if a slower model (e.g. FLUX.1-dev) were adopted.
- A 1024×1024 PNG is roughly 2MB once base64-encoded. The ComfyUI WebClient raises its in-memory codec limit to 32MB so there is headroom if larger sizes are ever enabled.
- The ComfyUI origin has no authentication of its own. `@PreAuthorize` protects this server's surface only — see Deployment for origin hardening.

See also
- Tools: ./tools.md
- Configuration: ./configuration.md
- Deployment: ./deployment.md
- Security: ./security.md
