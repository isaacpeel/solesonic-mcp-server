package com.solesonic.mcp.workflow.framework;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRunnerTest {

    @Test
    void runExecutesParallelAndSequentialGroupsAndCompletes() {
        WorkflowRunner workflowRunner = new WorkflowRunner();
        TestWorkflowContext workflowContext = new TestWorkflowContext();
        CapturingWorkflowNotificationService notificationService = new CapturingWorkflowNotificationService();
        WorkflowExecutionContext executionContext = executionContext(notificationService, Map.of(
                "step-one", 0.4,
                "step-two", 0.4,
                "step-three", 0.2
        ));

        AtomicInteger executionCounter = new AtomicInteger(0);

        WorkflowStep<TestWorkflowContext> stepOne = new StaticWorkflowStep("step-one", () -> {
            executionCounter.incrementAndGet();
            return Mono.just(WorkflowDecision.continueWorkflow());
        });
        WorkflowStep<TestWorkflowContext> stepTwo = new StaticWorkflowStep("step-two", () -> {
            executionCounter.incrementAndGet();
            return Mono.just(WorkflowDecision.continueWorkflow());
        });
        WorkflowStep<TestWorkflowContext> stepThree = new StaticWorkflowStep("step-three", () -> {
            executionCounter.incrementAndGet();
            return Mono.just(WorkflowDecision.continueWorkflow());
        });

        WorkflowDefinition<TestWorkflowContext> workflowDefinition = WorkflowDefinition.<TestWorkflowContext>builder("test-workflow")
                .parallel(stepOne, stepTwo)
                .sequential(stepThree)
                .build();

        StepVerifier.create(workflowRunner.run(workflowDefinition, workflowContext, executionContext))
                .expectNext(WorkflowOutcome.COMPLETED)
                .verifyComplete();

        assertEquals(3, executionCounter.get());
        assertTrue(notificationService.containsEventType(WorkflowEventType.WORKFLOW_STARTED));
        assertTrue(notificationService.containsEventType(WorkflowEventType.WORKFLOW_COMPLETED));
    }

    @Test
    void runStopsSequentialExecutionWhenUserInputIsRequired() {
        WorkflowRunner workflowRunner = new WorkflowRunner();
        TestWorkflowContext workflowContext = new TestWorkflowContext();
        CapturingWorkflowNotificationService notificationService = new CapturingWorkflowNotificationService();
        WorkflowExecutionContext executionContext = executionContext(notificationService, Map.of(
                "step-one", 0.5,
                "step-two", 0.5
        ));

        WorkflowPendingInput pendingInput = new WorkflowPendingInput(
                List.of(new WorkflowQuestion("question-1", "Need project?", "Missing field", true, "STRING", "project")),
                "resume-token",
                "RESOLVING_ASSIGNEE"
        );

        AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

        WorkflowStep<TestWorkflowContext> stepOne = new StaticWorkflowStep(
                "step-one",
                () -> Mono.just(WorkflowDecision.userInputRequired("Need clarification", pendingInput))
        );

        WorkflowStep<TestWorkflowContext> stepTwo = new StaticWorkflowStep("step-two", () -> {
            secondStepExecuted.set(true);
            return Mono.just(WorkflowDecision.continueWorkflow());
        });

        WorkflowDefinition<TestWorkflowContext> workflowDefinition = WorkflowDefinition.<TestWorkflowContext>builder("test-workflow")
                .sequential(stepOne, stepTwo)
                .build();

        StepVerifier.create(workflowRunner.run(workflowDefinition, workflowContext, executionContext))
                .expectNext(WorkflowOutcome.USER_INPUT_REQUIRED)
                .verifyComplete();

        assertFalse(secondStepExecuted.get());
        assertNotNull(executionContext.pendingInput());
        assertEquals("resume-token", executionContext.pendingInput().resumeToken());
        assertTrue(notificationService.containsEventType(WorkflowEventType.USER_INPUT_REQUIRED));
    }

    private WorkflowExecutionContext executionContext(
            WorkflowNotificationService notificationService,
            Map<String, Double> taskWeights
    ) {
        WorkflowProgressTracker progressTracker = new WorkflowProgressTracker(
                "test-workflow",
                "correlation-id",
                notificationService,
                taskWeights
        );
        return new WorkflowExecutionContext(
                "test-workflow",
                "correlation-id",
                notificationService,
                progressTracker,
                Map.of()
        );
    }

    private static final class TestWorkflowContext implements WorkflowContext {
    }

    private record StaticWorkflowStep(String name, SupplierSupplier supplierSupplier) implements WorkflowStep<TestWorkflowContext> {
        @Override
        public Mono<WorkflowDecision> execute(TestWorkflowContext context, WorkflowExecutionContext executionContext) {
            return supplierSupplier.get();
        }
    }

    private interface SupplierSupplier {
        Mono<WorkflowDecision> get();
    }

    private static final class CapturingWorkflowNotificationService implements WorkflowNotificationService {
        private final List<WorkflowEvent> events;

        private CapturingWorkflowNotificationService() {
            this.events = new CopyOnWriteArrayList<>();
        }

        @Override
        public void publish(WorkflowEvent event) {
            events.add(event);
        }

        private boolean containsEventType(WorkflowEventType workflowEventType) {
            return events.stream().anyMatch(event -> event.type() == workflowEventType);
        }
    }
}
