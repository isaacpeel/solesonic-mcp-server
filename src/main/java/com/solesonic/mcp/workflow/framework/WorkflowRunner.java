package com.solesonic.mcp.workflow.framework;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WorkflowRunner {

    public <C extends WorkflowContext> WorkflowOutcome run(
            WorkflowDefinition<C> definition,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(executionContext, "executionContext must not be null");

        executionContext.notificationService().publish(
                WorkflowEvent.workflowStarted(definition.name(), executionContext.correlationId(), "Workflow started")
        );

        try {
            WorkflowOutcome outcome = executeStepGroups(definition.stepGroups(), context, executionContext);
            handleWorkflowCompletion(definition, executionContext, outcome);
            return outcome;
        } catch (Exception exception) {
            executionContext.notificationService().publish(
                    WorkflowEvent.workflowFailed(definition.name(), executionContext.correlationId(), "Workflow failed", exception)
            );
            throw exception;
        }
    }

    private <C extends WorkflowContext> WorkflowOutcome executeStepGroups(
            List<WorkflowDefinition.StepGroup<C>> stepGroups,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        WorkflowOutcome currentOutcome = WorkflowOutcome.COMPLETED;

        for (WorkflowDefinition.StepGroup<C> stepGroup : stepGroups) {
            if (currentOutcome != WorkflowOutcome.COMPLETED) {
                break;
            }

            if (stepGroup.executionMode() == WorkflowDefinition.ExecutionMode.PARALLEL) {
                currentOutcome = executeParallelStepGroup(stepGroup.steps(), context, executionContext);
            } else {
                currentOutcome = executeSequentialStepGroup(stepGroup.steps(), context, executionContext);
            }
        }

        return currentOutcome;
    }

    private <C extends WorkflowContext> WorkflowOutcome executeSequentialStepGroup(
            List<WorkflowStep<C>> steps,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        WorkflowOutcome currentOutcome = WorkflowOutcome.COMPLETED;

        for (WorkflowStep<C> step : steps) {
            if (currentOutcome != WorkflowOutcome.COMPLETED) {
                break;
            }
            currentOutcome = executeStep(step, context, executionContext).outcome();
        }

        return currentOutcome;
    }

    private <C extends WorkflowContext> WorkflowOutcome executeParallelStepGroup(
            List<WorkflowStep<C>> steps,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        ExecutorService parallelStepExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            List<CompletableFuture<WorkflowDecision>> futures = steps.stream()
                    .map(step -> CompletableFuture.supplyAsync(
                            () -> executeStep(step, context, executionContext),
                            parallelStepExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<WorkflowDecision> decisions = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            return resolveParallelGroupOutcome(decisions, executionContext);
        } finally {
            parallelStepExecutor.shutdown();
        }
    }

    private WorkflowOutcome resolveParallelGroupOutcome(
            List<WorkflowDecision> decisions,
            WorkflowExecutionContext executionContext
    ) {
        if (decisions.stream()
                .anyMatch(decision -> decision.outcome() == WorkflowOutcome.USER_INPUT_REQUIRED)) {
            WorkflowDecision userInputDecision = decisions.stream()
                    .filter(decision -> decision.outcome() == WorkflowOutcome.USER_INPUT_REQUIRED)
                    .findFirst()
                    .orElseThrow();

            executionContext.setPendingInput(userInputDecision.pendingInput());
            return WorkflowOutcome.USER_INPUT_REQUIRED;
        }

        if (decisions.stream().anyMatch(decision -> decision.outcome() == WorkflowOutcome.FAILED)) {
            return WorkflowOutcome.FAILED;
        }

        return WorkflowOutcome.COMPLETED;
    }

    private <C extends WorkflowContext> WorkflowDecision executeStep(
            WorkflowStep<C> step,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        executionContext.notificationService().publish(
                WorkflowEvent.stepStarted(
                        executionContext.workflowName(),
                        step.name(),
                        executionContext.correlationId(),
                        "Step started"
                )
        );

        try {
            WorkflowDecision decision = step.execute(context, executionContext);
            if (decision == null) {
                decision = WorkflowDecision.continueWorkflow();
            }
            return handleStepDecision(step, executionContext, decision);
        } catch (Exception exception) {
            executionContext.notificationService().publish(
                    WorkflowEvent.stepFailed(
                            executionContext.workflowName(),
                            step.name(),
                            executionContext.correlationId(),
                            "Step failed",
                            exception
                    )
            );
            throw exception;
        }
    }

    private WorkflowDecision handleStepDecision(
            WorkflowStep<?> step,
            WorkflowExecutionContext executionContext,
            WorkflowDecision decision
    ) {
        if (decision.outcome() == WorkflowOutcome.USER_INPUT_REQUIRED) {
            executionContext.setPendingInput(decision.pendingInput());
            executionContext.notificationService().publish(
                    WorkflowEvent.userInputRequired(
                            executionContext.workflowName(),
                            step.name(),
                            executionContext.correlationId(),
                            messageOrDefault(decision.message(), "User input required")
                    )
            );
            return decision;
        }

        if (decision.outcome() == WorkflowOutcome.FAILED) {
            executionContext.notificationService().publish(
                    WorkflowEvent.stepFailed(
                            executionContext.workflowName(),
                            step.name(),
                            executionContext.correlationId(),
                            messageOrDefault(decision.message(), "Step failed"),
                            null
                    )
            );
            return decision;
        }

        executionContext.notificationService().publish(
                WorkflowEvent.stepCompleted(
                        executionContext.workflowName(),
                        step.name(),
                        executionContext.correlationId(),
                        messageOrDefault(decision.message(), "Step completed")
                )
        );
        return decision;
    }

    private void handleWorkflowCompletion(
            WorkflowDefinition<?> definition,
            WorkflowExecutionContext executionContext,
            WorkflowOutcome outcome
    ) {
        if (outcome == WorkflowOutcome.FAILED) {
            executionContext.notificationService().publish(
                    WorkflowEvent.workflowFailed(
                            definition.name(),
                            executionContext.correlationId(),
                            "Workflow failed",
                            null
                    )
            );
            return;
        }

        if (outcome == WorkflowOutcome.COMPLETED) {
            executionContext.notificationService().publish(
                    WorkflowEvent.workflowCompleted(definition.name(), executionContext.correlationId(), "Workflow completed")
            );
        }
    }

    private String messageOrDefault(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}
