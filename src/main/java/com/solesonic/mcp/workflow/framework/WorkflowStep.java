package com.solesonic.mcp.workflow.framework;

import reactor.core.publisher.Mono;

public interface WorkflowStep<C extends WorkflowContext> {
    String name();

    default boolean isParallelSafe() {
        return false;
    }

    Mono<WorkflowDecision> execute(C context, WorkflowExecutionContext executionContext);
}
