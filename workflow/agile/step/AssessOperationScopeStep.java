package com.solesonic.mcp.workflow.agile.step;

import com.solesonic.mcp.model.atlassian.agile.Board;
import com.solesonic.mcp.service.atlassian.JiraAgileService;
import com.solesonic.mcp.tool.atlassian.JiraAgileTools;
import com.solesonic.mcp.workflow.agile.AgileQueryResult;
import com.solesonic.mcp.workflow.agile.AgileQueryWorkflowContext;
import com.solesonic.mcp.workflow.agile.AgileWorkflowStage;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AssessOperationScopeStep implements WorkflowStep<AgileQueryWorkflowContext> {
    public static final String STEP_NAME = "assess-operation-scope";

    private static final Logger log = LoggerFactory.getLogger(AssessOperationScopeStep.class);

    static final int BATCH_THRESHOLD = 20;
    static final int DEFAULT_BATCH_SIZE = 20;

    private final JiraAgileService jiraAgileService;

    public AssessOperationScopeStep(JiraAgileService jiraAgileService) {
        this.jiraAgileService = jiraAgileService;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(AgileQueryWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(AgileWorkflowStage.ASSESSING_SCOPE);

        AgileQueryResult agileQueryResult = context.getAgileQueryResult();
        if (agileQueryResult == null || !agileQueryResult.isTransitionQuery()) {
            log.debug("Scope assessment skipped — not a transition query");
            executionContext.progressTracker().step(name()).done("Scope assessment skipped");
            return WorkflowDecision.skip("Non-transition query — scope assessment not required");
        }

        if (context.getBoards().isEmpty()) {
            log.debug("Scope assessment skipped — no boards available");
            executionContext.progressTracker().step(name()).done("No boards to assess");
            return WorkflowDecision.skip("No boards available for scope assessment");
        }

        Board board = context.getBoards().getFirst();
        String jqlFilter = agileQueryResult.jqlFilter() == null ? "" : agileQueryResult.jqlFilter().strip();

        executionContext.progressTracker().step(name()).update(0.3, "Counting matching issues on board '%s'".formatted(board.name()));

        JiraAgileTools.BoardIssuesRequest countRequest = new JiraAgileTools.BoardIssuesRequest(
                String.valueOf(board.id()),
                jqlFilter.isEmpty() ? null : jqlFilter,
                null,
                0,
                false
        );

        var boardIssues = jiraAgileService.getBoardIssues(countRequest);
        int totalCount = boardIssues.total() != null ? boardIssues.total() : 0;

        context.setEstimatedItemCount(totalCount);
        boolean needsBatching = totalCount > BATCH_THRESHOLD;
        context.setRequiresBatching(needsBatching);
        context.setBatchSize(DEFAULT_BATCH_SIZE);

        log.info("Scope assessment: {} items found, batching={}", totalCount, needsBatching);
        executionContext.progressTracker().step(name()).done(
                "Found %d issue(s)%s".formatted(totalCount, needsBatching ? " — batching required" : "")
        );

        return WorkflowDecision.continueWorkflow();
    }
}
