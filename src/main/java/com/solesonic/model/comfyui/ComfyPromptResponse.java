package com.solesonic.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * The {@code POST /prompt} response. ComfyUI answers HTTP 200 with a populated {@code node_errors}
 * when the workflow is malformed, so the status code alone is not a success signal.
 */
public record ComfyPromptResponse(
        @JsonProperty("prompt_id") String promptId,
        Integer number,
        @JsonProperty("node_errors") Map<String, Object> nodeErrors
) {}
