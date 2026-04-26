package com.solesonic.mcp.workflow.framework;

import org.junit.jupiter.api.Test;

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
            return WorkflowDecision.continueWorkflow();
        });
        WorkflowStep<TestWorkflowContext> stepTwo = new StaticWorkflowStep("step-two", () -> {
            executionCounter.incrementAndGet();
            return WorkflowDecision.continueWorkflow();
        });
        WorkflowStep<TestWorkflowContext> stepThree = new StaticWorkflowStep("step-three", () -> {
            executionCounter.incrementAndGet();
            return WorkflowDecision.continueWorkflow();
        });

        WorkflowDefinition<TestWorkflowContext> workflowDefinition = WorkflowDefinition.<TestWorkflowContext>builder("test-workflow")
                .parallel(stepOne, stepTwo)
                .sequential(stepThree)
                .build();

        WorkflowOutcome outcome = workflowRunner.run(workflowDefinition, workflowContext, executionContext);

        assertEquals(WorkflowOutcome.COMPLETED, outcome);
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
                () -> WorkflowDecision.userInputRequired("Need clarification", pendingInput)
        );

        WorkflowStep<TestWorkflowContext> stepTwo = new StaticWorkflowStep("step-two", () -> {
            secondStepExecuted.set(true);
            return WorkflowDecision.continueWorkflow();
        });

        WorkflowDefinition<TestWorkflowContext> workflowDefinition = WorkflowDefinition.<TestWorkflowContext>builder("test-workflow")
                .sequential(stepOne, stepTwo)
                .build();

        WorkflowOutcome outcome = workflowRunner.run(workflowDefinition, workflowContext, executionContext);

        assertEquals(WorkflowOutcome.USER_INPUT_REQUIRED, outcome);
        assertFalse(secondStepExecuted.get());
        assertNotNull(executionContext.pendingInput());
        assertEquals("resume-token", executionContext.pendingInput().resumeToken());
        assertTrue(notificationService.containsEventType(WorkflowEventType.USER_INPUT_REQUIRED));
    }

    private WorkflowExecutionContext executionContext(
            WorkflowNotificationService notificationService,
            Map<String, Double> taskWeights
    ) {
        return new WorkflowExecutionContext(
                "test-workflow",
                "correlation-id",
                notificationService,
                taskWeights,
                Map.of()
        );
    }

    private static final class TestWorkflowContext implements WorkflowContext {
    }

    private record StaticWorkflowStep(String name, DecisionSupplier decisionSupplier) implements WorkflowStep<TestWorkflowContext> {
        @Override
        public boolean isParallelSafe() {
            return true;
        }

        @Override
        public WorkflowDecision execute(TestWorkflowContext context, WorkflowExecutionContext executionContext) {
            return decisionSupplier.get();
        }
    }

    private interface DecisionSupplier {
        WorkflowDecision get();
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
