package com.solesonic.agent.image;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;

/**
 * The intent step's answer, and the structured-output type the routing model fills in. A null
 * {@code intent} means the intent could not be decided; workflow selection then offers every
 * workflow that accepts the attached images. Serializable because graph state is deep-copied with
 * Java serialization and checkpointed to Redis.
 */
public record ImageIntentClassification(
        @JsonPropertyDescription("EDIT_PERSON_IN_PHOTO or RESTYLE_PHOTO.")
        ImageIntent intent,

        @JsonPropertyDescription("One sentence explaining the choice.")
        String rationale
) implements Serializable {
}
