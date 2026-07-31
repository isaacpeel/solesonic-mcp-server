package com.solesonic.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * The {@code POST /prompt} envelope. {@code prompt} is the patched API-format workflow graph.
 */
public record ComfyPromptRequest(
        JsonNode prompt,
        @JsonProperty("client_id") String clientId
) {}
