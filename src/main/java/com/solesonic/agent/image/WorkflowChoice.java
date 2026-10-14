package com.solesonic.agent.image;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured output of the workflow selection step: which workflow, and why. The prompt is
 * written later, by a separate step that knows the chosen workflow.
 */
public record WorkflowChoice(
        @JsonPropertyDescription("The toolName of exactly one workflow from the list offered.")
        String toolName,

        @JsonPropertyDescription("One sentence explaining why this workflow fits the request.")
        String rationale
) {
}
