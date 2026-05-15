package com.solesonic.mcp.workflow.jira;

import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowDefinition;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowNotificationService;
import com.solesonic.a2a.workflow.framework.WorkflowOutcome;
import com.solesonic.a2a.workflow.framework.WorkflowPendingInput;
import com.solesonic.a2a.workflow.framework.WorkflowQuestion;
import com.solesonic.a2a.workflow.framework.WorkflowRunner;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.jira.CreateJiraWorkflowContext;
import com.solesonic.a2a.workflow.jira.CreateJiraWorkflowDefinition;
import com.solesonic.a2a.workflow.jira.CreateJiraWorkflowService;
import com.solesonic.a2a.workflow.jira.WorkflowStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateJiraWorkflowServiceTest {

    @Mock
    private WorkflowRunner workflowRunner;

    @Mock
    private CreateJiraWorkflowDefinition createJiraWorkflowDefinition;

    @Test
    void runSetsUserInputFlagsWhenRunnerRequestsUserInput() {
        CreateJiraWorkflowContext workflowContext = new CreateJiraWorkflowContext("Create an onboarding workflow issue");
        WorkflowExecutionContext executionContext = executionContext();
        WorkflowPendingInput pendingInput = new WorkflowPendingInput(
                List.of(new WorkflowQuestion("q1", "Need assignee", "No assignee found", true, "STRING", "assignee")),
                "resume-token",
                "RESOLVING_ASSIGNEE"
        );
        executionContext.setPendingInput(pendingInput);

        WorkflowDefinition<CreateJiraWorkflowContext> workflowDefinition = WorkflowDefinition.<CreateJiraWorkflowContext>builder("create-jira")
                .sequential(new NoopStep())
                .build();

        when(createJiraWorkflowDefinition.definition()).thenReturn(workflowDefinition);
        when(workflowRunner.run(eq(workflowDefinition), eq(workflowContext), eq(executionContext)))
                .thenReturn(WorkflowOutcome.USER_INPUT_REQUIRED);

        CreateJiraWorkflowService createJiraWorkflowService = new CreateJiraWorkflowService(workflowRunner, createJiraWorkflowDefinition);

        WorkflowOutcome outcome = createJiraWorkflowService.run(workflowContext, executionContext);

        assertEquals(WorkflowOutcome.USER_INPUT_REQUIRED, outcome);
        assertTrue(workflowContext.isRequiresUserInput());
        assertEquals(WorkflowStage.USER_INPUT_REQUIRED, workflowContext.getCurrentStage());
        assertEquals("resume-token", workflowContext.getPendingInput().resumeToken());
    }

    private WorkflowExecutionContext executionContext() {
        WorkflowNotificationService notificationService = _ -> {
        };

        return new WorkflowExecutionContext(
                "create-jira",
                "correlation-id",
                notificationService,
                Map.of("noop", 1.0),
                Map.of()
        );
    }

    private static final class NoopStep implements WorkflowStep<CreateJiraWorkflowContext> {
        @Override
        public String name() {
            return "noop";
        }

        @Override
        public WorkflowDecision execute(
                CreateJiraWorkflowContext context,
                WorkflowExecutionContext executionContext
        ) {
            return WorkflowDecision.continueWorkflow();
        }
    }
}
