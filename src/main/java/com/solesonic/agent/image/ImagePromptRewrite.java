package com.solesonic.agent.image;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured output of the prompt-writing step.
 */
public record ImagePromptRewrite(
        @JsonPropertyDescription("The final prompt sent to the image model.")
        String enhancedPrompt
) {
}
