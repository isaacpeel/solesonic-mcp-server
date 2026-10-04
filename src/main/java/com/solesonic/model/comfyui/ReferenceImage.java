package com.solesonic.model.comfyui;

/**
 * A caller-supplied image, already decoded and verified, that an image-guided workflow loads
 * through a {@code LoadImage} node. Never serialized and never logged.
 */
public record ReferenceImage(
        byte[] bytes,
        String mimeType
) {
}
