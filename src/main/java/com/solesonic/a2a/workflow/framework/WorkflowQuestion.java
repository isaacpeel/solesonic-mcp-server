package com.solesonic.a2a.workflow.framework;

public record WorkflowQuestion(
        String id,
        String prompt,
        String reason,
        boolean required,
        String expectedAnswerType,
        String targetField
) {
}
