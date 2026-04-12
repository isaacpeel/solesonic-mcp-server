package com.solesonic.mcp.workflow.framework;

public record WorkflowQuestion(
        String id,
        String prompt,
        String reason,
        boolean required,
        String expectedAnswerType,
        String targetField
) {
}
