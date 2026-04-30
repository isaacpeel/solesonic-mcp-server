package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilyExtractResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchEspnScheduleStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-schedule";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnScheduleStep.class);

    private final TavilySearchService tavilySearchService;

    public FetchEspnScheduleStep(TavilySearchService tavilySearchService) {
        this.tavilySearchService = tavilySearchService;
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
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        List<EspnTeamProfile> resolvedTeams = context.getResolvedTeams();
        if (resolvedTeams == null || resolvedTeams.isEmpty()) {
            log.info("No resolved teams — skipping ESPN schedule fetch");
            return WorkflowDecision.skip("No teams resolved for schedule fetch");
        }

        context.setCurrentStage(SportsWorkflowStage.FETCHING_ESPN_SCHEDULE);
        executionContext.progressTracker().step(name()).update(0.2, "Fetching team schedules from ESPN");

        List<String> scheduleUrls = resolvedTeams.stream()
                .map(EspnTeamProfile::scheduleUrl)
                .toList();

        log.info("Fetching ESPN schedule pages: {}", scheduleUrls);

        try {
            TavilyExtractResponse response = tavilySearchService.extract(scheduleUrls);
            String scheduleData = formatExtractResults(response, "schedule");
            context.setEspnScheduleData(scheduleData);
            executionContext.progressTracker().step(name()).done("ESPN schedule data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN schedule pages", exception);
            context.setEspnScheduleData("ESPN schedule data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }

    private String formatExtractResults(TavilyExtractResponse response, String label) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return "No ESPN %s data retrieved.".formatted(label);
        }
        StringBuilder builder = new StringBuilder();
        for (var result : response.results()) {
            builder.append("=== ESPN Schedule: ").append(result.url()).append(" ===\n");
            builder.append(result.rawContent()).append("\n\n");
        }
        return builder.toString();
    }
}
