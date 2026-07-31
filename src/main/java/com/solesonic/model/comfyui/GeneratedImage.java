package com.solesonic.model.comfyui;

/**
 * The service's own response boundary. Carries the resolved parameters back so the tool can render
 * its metadata block — most importantly the seed, which is what makes a good image reproducible.
 */
public record GeneratedImage(
        String base64Png,
        int width,
        int height,
        int steps,
        long seed,
        double elapsedSeconds
) {}
