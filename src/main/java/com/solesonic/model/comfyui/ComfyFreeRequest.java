package com.solesonic.model.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code POST /free} envelope, used to release VRAM/RAM back to the OS after a generation.
 */
public record ComfyFreeRequest(
        @JsonProperty("unload_models") boolean unloadModels,
        @JsonProperty("free_memory") boolean freeMemory
) {}