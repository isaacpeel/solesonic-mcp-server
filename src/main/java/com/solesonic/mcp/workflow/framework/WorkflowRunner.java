package com.solesonic.mcp.workflow.framework;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
public class WorkflowRunner {

    public <C extends WorkflowContext> Mono<WorkflowOutcome> run(
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

        return executeStepGroups(definition.stepGroups(), context, executionContext)
                .doOnSuccess(outcome -> handleWorkflowCompletion(definition, executionContext, outcome))
                .onErrorResume(error -> {
                    executionContext.notificationService().publish(
                            WorkflowEvent.workflowFailed(definition.name(), executionContext.correlationId(), "Workflow failed", error)
                    );
                    return Mono.error(error);
                });
    }

    private <C extends WorkflowContext> Mono<WorkflowOutcome> executeStepGroups(
            List<WorkflowDefinition.StepGroup<C>> stepGroups,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        Mono<WorkflowOutcome> outcome = Mono.just(WorkflowOutcome.COMPLETED);

        for (WorkflowDefinition.StepGroup<C> stepGroup : stepGroups) {
            outcome = outcome.flatMap(currentOutcome -> {
                if (currentOutcome != WorkflowOutcome.COMPLETED) {
                    return Mono.just(currentOutcome);
                }

                if (stepGroup.executionMode() == WorkflowDefinition.ExecutionMode.PARALLEL) {
                    return executeParallelStepGroup(stepGroup.steps(), context, executionContext);
                }

                return executeSequentialStepGroup(stepGroup.steps(), context, executionContext);
            });
        }

        return outcome;
    }

    private <C extends WorkflowContext> Mono<WorkflowOutcome> executeSequentialStepGroup(
            List<WorkflowStep<C>> steps,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        Mono<WorkflowOutcome> outcome = Mono.just(WorkflowOutcome.COMPLETED);

        for (WorkflowStep<C> step : steps) {
            outcome = outcome.flatMap(currentOutcome -> {
                if (currentOutcome != WorkflowOutcome.COMPLETED) {
                    return Mono.just(currentOutcome);
                }

                return executeStep(step, context, executionContext).map(WorkflowDecision::outcome);
            });
        }

        return outcome;
    }

    private <C extends WorkflowContext> Mono<WorkflowOutcome> executeParallelStepGroup(
            List<WorkflowStep<C>> steps,
            C context,
            WorkflowExecutionContext executionContext
    ) {
        return Flux.fromIterable(steps)
                .flatMap(step -> executeStep(step, context, executionContext))
                .collectList()
                .map(decisions -> resolveParallelGroupOutcome(decisions, executionContext));
    }

    private WorkflowOutcome resolveParallelGroupOutcome(
            List<WorkflowDecision> decisions,
            WorkflowExecutionContext executionContext
    ) {
        if (decisions.stream().anyMatch(decision -> decision.outcome() == WorkflowOutcome.USER_INPUT_REQUIRED)) {
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

    private <C extends WorkflowContext> Mono<WorkflowDecision> executeStep(
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

        return step.execute(context, executionContext)
                .defaultIfEmpty(WorkflowDecision.continueWorkflow())
                .map(decision -> handleStepDecision(step, executionContext, decision))
                .onErrorResume(error -> {
                    executionContext.notificationService().publish(
                            WorkflowEvent.stepFailed(
                                    executionContext.workflowName(),
                                    step.name(),
                                    executionContext.correlationId(),
                                    "Step failed",
                                    error
                            )
                    );
                    return Mono.error(error);
                });
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
