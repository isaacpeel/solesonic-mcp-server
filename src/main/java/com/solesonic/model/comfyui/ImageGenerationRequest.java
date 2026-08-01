package com.solesonic.model.comfyui;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The service's own request boundary. Never serialized — ComfyUI's node vocabulary stops here.
 */
public record ImageGenerationRequest(
        String prompt,
        int width,
        int height,
        int steps,
        long seed
) {
    /**
     * Fixed generation parameters. The tool exposes only a prompt, so these are not caller-tunable:
     * FLUX.1-schnell is distilled for 4 steps, and 1024x1024 is its native resolution.
     */
    public static final int DEFAULT_WIDTH = 1024;
    public static final int DEFAULT_HEIGHT = 1024;
    public static final int DEFAULT_STEPS = 4;

    /**
     * Builds a request from a prompt alone: the fixed generation parameters plus a fresh random
     * seed, so every call lands on a distinct run in ComfyUI's history.
     */
    public ImageGenerationRequest(String prompt) {
        long randomSeed = ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);

        this(prompt, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_STEPS, randomSeed);
    }
}
