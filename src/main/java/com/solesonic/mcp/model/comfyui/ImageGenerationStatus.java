package com.solesonic.mcp.model.comfyui;

/**
 * Represents the high-level status of an image generation job.
 * Provides a simplified view of the generation progress.
 */
public record ImageGenerationStatus(
        State state,
        String promptId,
        String message,
        int progressPercent,
        String currentNode
) {

    /**
     * Possible states of an image generation job.
     */
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    /**
     * Creates a QUEUED status.
     */
    public static ImageGenerationStatus queued(String promptId, String message) {
        return new ImageGenerationStatus(State.QUEUED, promptId, message, 0, null);
    }

    /**
     * Creates a RUNNING status with progress information.
     */
    public static ImageGenerationStatus running(String promptId, String message, int progressPercent, String currentNode) {
        return new ImageGenerationStatus(State.RUNNING, promptId, message, progressPercent, currentNode);
    }

    /**
     * Creates a RUNNING status without progress information.
     */
    public static ImageGenerationStatus running(String promptId, String message) {
        return new ImageGenerationStatus(State.RUNNING, promptId, message, 0, null);
    }

    /**
     * Creates a COMPLETED status.
     */
    public static ImageGenerationStatus completed(String promptId, String message) {
        return new ImageGenerationStatus(State.COMPLETED, promptId, message, 100, null);
    }

    /**
     * Creates a FAILED status.
     */
    public static ImageGenerationStatus failed(String promptId, String message) {
        return new ImageGenerationStatus(State.FAILED, promptId, message, 0, null);
    }

    /**
     * Returns true if this status represents a terminal state (COMPLETED or FAILED).
     */
    public boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED;
    }
}
