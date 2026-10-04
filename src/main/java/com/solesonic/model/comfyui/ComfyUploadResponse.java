package com.solesonic.model.comfyui;

/**
 * The {@code POST /upload/image} response: where ComfyUI stored the file, which is what a
 * {@code LoadImage} node is then given.
 */
public record ComfyUploadResponse(
        String name,
        String subfolder,
        String type
) {

    /**
     * The value a {@code LoadImage} node's {@code image} input expects.
     */
    public String loadImageValue() {
        return subfolder == null || subfolder.isEmpty() ? name : subfolder + "/" + name;
    }
}
