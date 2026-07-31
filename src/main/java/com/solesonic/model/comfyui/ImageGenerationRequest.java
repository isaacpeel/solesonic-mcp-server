package com.solesonic.model.comfyui;

/**
 * The service's own request boundary. Never serialized — ComfyUI's node vocabulary stops here.
 */
public record ImageGenerationRequest(
        String prompt,
        int width,
        int height,
        int steps,
        long seed
) {}
