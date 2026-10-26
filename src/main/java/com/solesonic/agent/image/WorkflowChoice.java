package com.solesonic.agent.image;

/**
 * The workflow selection step's answer: which workflow, and why. A null {@code toolName} means no
 * workflow was chosen and the first candidate is used. The prompt is written later, by a separate
 * step that knows the chosen workflow.
 */
public record WorkflowChoice(String toolName, String rationale) {
}
