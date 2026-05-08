package com.solesonic.mcp.workflow.framework;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowProgressTrackerTest {

    @Test
    void startupAndStepUpdatesEmitWeightedProgress() {
        CapturingWorkflowNotificationService notificationService = new CapturingWorkflowNotificationService();
        WorkflowProgressTracker workflowProgressTracker = new WorkflowProgressTracker(
                "test-workflow",
                "correlation-id",
                notificationService,
                Map.of(
                        "user-story-generation", 0.70,
                        "assignee-lookup", 0.20,
                        "finalize", 0.10
                )
        );

        workflowProgressTracker.startup("Starting");
        workflowProgressTracker.startup("Preparing");
        workflowProgressTracker.step("user-story-generation").update(0.5, "Story in progress");

        List<WorkflowEvent> emittedProgressEvents = notificationService.progressEvents();
        assertEquals(3, emittedProgressEvents.size());
        assertEquals(1, emittedProgressEvents.getFirst().percent());
        assertEquals(2, emittedProgressEvents.get(1).percent());
        assertEquals(41, emittedProgressEvents.get(2).percent());
    }

    @Test
    void stepThrowsWhenStepNameIsUnknown() {
        CapturingWorkflowNotificationService notificationService = new CapturingWorkflowNotificationService();
        WorkflowProgressTracker workflowProgressTracker = new WorkflowProgressTracker(
                "test-workflow",
                "correlation-id",
                notificationService,
                Map.of("known-step", 1.0)
        );

        assertThrows(IllegalArgumentException.class, () -> workflowProgressTracker.step("unknown-step"));
    }

    private static final class CapturingWorkflowNotificationService implements WorkflowNotificationService {
        private final List<WorkflowEvent> events;

        private CapturingWorkflowNotificationService() {
            this.events = new ArrayList<>();
        }

        @Override
        public void publish(WorkflowEvent event) {
            events.add(event);
        }

        private List<WorkflowEvent> progressEvents() {
            return events.stream()
                    .filter(event -> event.type() == WorkflowEventType.STEP_PROGRESS)
                    .toList();
        }
    }
}
