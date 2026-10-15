package com.solesonic.model.comfyui;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One image-guided graph of a stored workflow: the graph used when a caller attaches exactly
 * {@code imageCount} reference images. A workflow keeps one per image count it accepts, so the same
 * tool can take one photo or several, each with a graph wired for that many {@code LoadImage} nodes.
 *
 * <p>{@code imageCount} must agree with the graph's {@code __REFERENCE_IMAGE_n__} tokens;
 * {@code ComfyWorkflowRegistry} drops a variant where it does not.
 */
@Embeddable
public record ComfyWorkflowReferenceVariant(
        @Column(name = "image_count", nullable = false) int imageCount,
        @Column(name = "workflow_json", nullable = false) String workflowJson
) {
}
