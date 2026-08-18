package com.solesonic.model.comfyui;

/**
 * The service's own response boundary. Carries the resolved parameters back so the tool can render
 * its metadata block — most importantly the seed, which is what makes a good image reproducible.
 */
public record GeneratedImage(
        String base64Png,
        int width,
        int height,
        long seed,
        double elapsedSeconds
) {

    /**
     * Starts a build from the request that produced the image: every generation parameter is copied
     * off the request, leaving only the results — the encoded image and how long it took — to set.
     */
    public static Builder imageGenerationRequest(final ImageGenerationRequest imageGenerationRequest) {
        return new Builder(imageGenerationRequest);
    }

    public static class Builder {
        private String base64Png;
        private final int width;
        private final int height;
        private final long seed;
        private double elapsedSeconds;

        public Builder(final ImageGenerationRequest imageGenerationRequest) {
            this.width = imageGenerationRequest.width();
            this.height = imageGenerationRequest.height();
            this.seed = imageGenerationRequest.seed();
        }

        public Builder base64Png(final String base64Png) {
            this.base64Png = base64Png;
            return this;
        }

        public Builder elapsedSeconds(final double elapsedSeconds) {
            this.elapsedSeconds = elapsedSeconds;
            return this;
        }

        public GeneratedImage build() {
            return new GeneratedImage(base64Png, width, height, seed, elapsedSeconds);
        }
    }
}
