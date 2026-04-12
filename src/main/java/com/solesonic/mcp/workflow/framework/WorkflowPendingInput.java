package com.solesonic.mcp.workflow.framework;

import java.util.List;

public record WorkflowPendingInput(List<WorkflowQuestion> questions, String resumeToken, String currentStage) {
}
