package com.solesonic.agent.image;

import java.io.Serializable;

/**
 * A workflow the router may choose for this request: one that accepts exactly the number of
 * reference images attached. Serializable because graph state is deep-copied with Java serialization.
 */
public record WorkflowCandidate(String toolName, String name, String description) implements Serializable {
}
