package com.solesonic.model.comfyui;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The service's own request boundary. Never serialized — ComfyUI's node vocabulary stops here.
 *
 * <p>Only values a caller can vary per invocation live here. Everything else about a generation —
 * steps, cfg, sampler, scheduler, checkpoint — is baked into the stored workflow, because the choice
 * of workflow <em>is</em> the choice of those values.
 */
public record ImageGenerationRequest(
        String prompt,
        int width,
        int height,
        long seed
) {
    /**
     * Defaults for the optional {@code width} and {@code height} tool parameters. These live in code
     * rather than in the workflow table on purpose: the table holds no tuning values at all.
     */
    public static final int DEFAULT_WIDTH = 1024;
    public static final int DEFAULT_HEIGHT = 1024;

    /**
     * Builds a request from a prompt alone: the default dimensions plus a fresh random seed, so
     * every call lands on a distinct run in ComfyUI's history.
     */
    public ImageGenerationRequest(String prompt) {
        this(prompt, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * A fresh random seed per call is what keeps repeated calls with the same prompt from returning
     * the byte-identical image — a workflow that omits {@code __SEED__} opts out of that
     * deliberately.
     */
    public ImageGenerationRequest(String prompt, int width, int height) {
        this(prompt, width, height, ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE));
    }
}
