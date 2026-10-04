package com.solesonic.mcp.exception.comfyui;

/**
 * A caller-supplied reference image that cannot be used. The message is written for the caller —
 * it becomes the text of an error tool result — and never carries the image data.
 */
public class InvalidReferenceImageException extends RuntimeException {

    public InvalidReferenceImageException(String message) {
        super(message);
    }
}
