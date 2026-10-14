package com.solesonic.agent.image;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;

/**
 * The router's choice of workflow, and the structured-output type the routing model fills in.
 * {@code toolName} and {@code enhancedPrompt} are null when the routing model failed; the resolve
 * node replaces them before the graph ends. Serializable because a checkpointed graph deep-copies
 * its state with Java serialization at every step.
 */
public record ImageWorkflowSelection(
        @JsonPropertyDescription("The toolName of exactly one workflow from the list offered.")
        String toolName,

        @JsonPropertyDescription("The final prompt sent to the image model.")
        String enhancedPrompt,

        @JsonPropertyDescription("One sentence explaining why this workflow fits the request.")
        String rationale
) implements Serializable {
}
