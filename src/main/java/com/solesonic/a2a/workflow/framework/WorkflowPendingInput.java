package com.solesonic.a2a.workflow.framework;

import java.util.List;

public record WorkflowPendingInput(List<WorkflowQuestion> questions, String resumeToken, String currentStage) {
}
