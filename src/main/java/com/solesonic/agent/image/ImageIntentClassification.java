package com.solesonic.agent.image;

import java.io.Serializable;

/**
 * The intent step's answer. A null {@code intent} means the intent could not be decided; workflow
 * selection then offers every workflow that accepts the attached images. Serializable because graph
 * state is deep-copied with Java serialization and checkpointed to Redis.
 */
public record ImageIntentClassification(ImageIntent intent, String rationale) implements Serializable {
}
