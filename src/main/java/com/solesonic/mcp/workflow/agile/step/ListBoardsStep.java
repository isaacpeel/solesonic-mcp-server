package com.solesonic.mcp.workflow.agile.step;

import com.solesonic.mcp.service.atlassian.JiraAgileService;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.workflow.agile.AgileWorkflowStage;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ListBoardsStep implements WorkflowStep<AgileQueryWorkflowContext> {
    public static final String STEP_NAME = "list-boards";

    private static final Logger log = LoggerFactory.getLogger(ListBoardsStep.class);

    private final JiraAgileService jiraAgileService;

    public ListBoardsStep(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public boolean isParallelSafe() {
        return true;
    }

    @Override
    public Mono<WorkflowDecision> execute(AgileQueryWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(AgileWorkflowStage.LISTING_BOARDS);
        executionContext.progressTracker().step(name()).update(0.1, "Fetching available boards");

        JiraAgileTools.ListBoardsRequest listBoardsRequest = new JiraAgileTools.ListBoardsRequest(
                null, null, null, null, null
        );

        return jiraAgileService.listBoards(listBoardsRequest)
                .map(boards -> {
                    log.info("Found {} accessible boards", boards.values().size());
                    context.setBoards(boards.values());
                    executionContext.progressTracker().step(name()).done("Boards fetched");
                    return WorkflowDecision.continueWorkflow();
                });
    }
}
