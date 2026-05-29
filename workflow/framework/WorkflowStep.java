package com.solesonic.mcp.workflow.framework;

public interface WorkflowStep<C extends WorkflowContext> {
    String name();

    default boolean isParallelSafe() {
        return false;
    }

    WorkflowDecision execute(C context, WorkflowExecutionContext executionContext);
}
