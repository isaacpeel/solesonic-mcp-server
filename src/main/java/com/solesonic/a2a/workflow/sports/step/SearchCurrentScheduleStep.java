package com.solesonic.a2a.workflow.sports.step;

import com.solesonic.mcp.model.espn.EspnScheduleSummary;
import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.a2a.workflow.sports.SportsWorkflowStage;
import com.solesonic.a2a.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SearchCurrentScheduleStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "search-current-schedule";

    private static final Logger log = LoggerFactory.getLogger(SearchCurrentScheduleStep.class);

    private static final int SUFFICIENT_CONTENT_MIN_CHARS = 300;
    public static final String NBA = "NBA";

    private final FetchEspnScheduleStep fetchEspnScheduleStep;
    private final FetchNbaComScheduleStep fetchNbaComScheduleStep;
    private final SearchTavilyScheduleStep searchTavilyScheduleStep;

    public SearchCurrentScheduleStep(
            FetchEspnScheduleStep fetchEspnScheduleStep,
            FetchNbaComScheduleStep fetchNbaComScheduleStep,
            SearchTavilyScheduleStep searchTavilyScheduleStep
    ) {
        this.fetchEspnScheduleStep = fetchEspnScheduleStep;
        this.fetchNbaComScheduleStep = fetchNbaComScheduleStep;
        this.searchTavilyScheduleStep = searchTavilyScheduleStep;
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
    public WorkflowDecision execute(SportsResearchWorkflowContext sportsResearchWorkflowContext, WorkflowExecutionContext executionContext) {
        sportsResearchWorkflowContext.setCurrentStage(SportsWorkflowStage.SEARCHING_SCHEDULE);
        executionContext.progressTracker().step(name()).update(0.1, "Fetching schedule from ESPN");

        SportsQueryIntent sportsQueryIntent = sportsResearchWorkflowContext.getSportsQueryIntent();
        String currentDate = sportsResearchWorkflowContext.currentDateTime();
        String teamQuery = buildTeamQueryString(sportsQueryIntent);

        // Tier 1: ESPN JSON API — structured, no scraping
        EspnScheduleSummary espnScheduleSummary = fetchEspnScheduleStep.fetch(sportsResearchWorkflowContext);

        if (espnScheduleSummary.hasUpcomingOrLiveGames()) {
            log.info("Schedule found on ESPN — skipping NBA.com and Tavily");
            sportsResearchWorkflowContext.setScheduleSearchSummary(espnScheduleSummary.toFormattedString());
            executionContext.progressTracker().step(name()).done("Schedule found on ESPN");
            return WorkflowDecision.continueWorkflow();
        }

        // Tier 2: Direct NBA.com extract
        executionContext.progressTracker().step(name()).update(0.5, "ESPN insufficient — trying NBA.com");
        String nbaResult = fetchNbaComScheduleStep.fetch();
        if (isSufficient(nbaResult)) {
            log.info("Schedule found on NBA.com — skipping Tavily");
            sportsResearchWorkflowContext.setScheduleSearchSummary(nbaResult);
            executionContext.progressTracker().step(name()).done("Schedule found on NBA.com");
            return WorkflowDecision.continueWorkflow();
        }

        // Tier 3: Broad Tavily search — last resort, no domain restrictions
        executionContext.progressTracker().step(name()).update(0.75, "Falling back to Tavily web search");
        log.info("ESPN and NBA.com insufficient — falling back to Tavily");
        sportsResearchWorkflowContext.setScheduleSearchSummary(searchTavilyScheduleStep.fetch(teamQuery, currentDate));
        executionContext.progressTracker().step(name()).done("Schedule search complete via Tavily");
        return WorkflowDecision.continueWorkflow();
    }

    private boolean isSufficient(String content) {
        return content != null && content.length() > SUFFICIENT_CONTENT_MIN_CHARS;
    }

    private String buildTeamQueryString(SportsQueryIntent intent) {
        if (intent != null && intent.hasTeams()) {
            return String.join(" ", intent.teams());
        }

        return NBA;
    }
}
