package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.tavily.TavilySearchRequest;
import com.solesonic.mcp.model.tavily.TavilySearchResponse;
import com.solesonic.mcp.service.tavily.TavilySearchService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.solesonic.mcp.config.tavily.TavilyConstants.*;

@Component
public class SearchCurrentScheduleStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "search-current-schedule";

    private static final Logger log = LoggerFactory.getLogger(SearchCurrentScheduleStep.class);

    private static final List<String> SPORTS_SCHEDULE_DOMAINS = List.of(
            "nba.com", "espn.com", "cbssports.com", "sports.yahoo.com"
    );

    private final TavilySearchService tavilySearchService;

    public SearchCurrentScheduleStep(TavilySearchService tavilySearchService) {
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
        context.setCurrentStage(SportsWorkflowStage.SEARCHING_SCHEDULE);
        executionContext.progressTracker().step(name()).update(0.1, "Searching for current game schedules");

        SportsQueryIntent intent = context.getSportsQueryIntent();
        String currentDate = context.getCurrentDateTime().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
        String teamQuery = buildTeamQueryString(intent);

        StringBuilder summary = new StringBuilder();

        try {
            // News search — news index has the freshest game time data
            String newsQuery = "%s next game schedule %s".formatted(teamQuery, currentDate);
            log.info("Executing news schedule search: {}", newsQuery);

            TavilySearchRequest newsRequest = TavilySearchRequest.builder()
                    .query(newsQuery)
                    .searchDepth(DEPTH_BASIC)
                    .topic(TOPIC_NEWS)
                    .maxResults(5)
                    .includeAnswer(true)
                    .timeRange(TIME_DAY)
                    .build();

            executionContext.progressTracker().step(name()).update(0.4, "Checking latest schedule news");
            TavilySearchResponse newsResponse = tavilySearchService.search(newsRequest);
            summary.append("=== News Search Results ===\n");
            summary.append(formatSearchResults(newsResponse));
            summary.append("\n");
        } catch (Exception exception) {
            log.warn("News schedule search failed: {}", exception.getMessage());
            summary.append("=== News Search Results ===\nSearch unavailable.\n\n");
        }

        try {
            // Advanced search scoped to authoritative sports sites for schedule data
            String advancedQuery = "%s schedule %s".formatted(teamQuery, currentDate);
            log.info("Executing advanced schedule search: {}", advancedQuery);

            TavilySearchRequest advancedRequest = TavilySearchRequest.builder()
                    .query(advancedQuery)
                    .searchDepth(DEPTH_ADVANCED)
                    .topic(TOPIC_GENERAL)
                    .maxResults(5)
                    .includeAnswer(true)
                    .includeDomains(SPORTS_SCHEDULE_DOMAINS)
                    .build();

            executionContext.progressTracker().step(name()).update(0.7, "Verifying schedule on official sites");
            TavilySearchResponse advancedResponse = tavilySearchService.search(advancedRequest);
            summary.append("=== Official Sites Search Results ===\n");
            summary.append(formatSearchResults(advancedResponse));
        } catch (Exception exception) {
            log.warn("Advanced schedule search failed: {}", exception.getMessage());
            summary.append("=== Official Sites Search Results ===\nSearch unavailable.\n");
        }

        context.setScheduleSearchSummary(summary.toString());
        executionContext.progressTracker().step(name()).done("Schedule search complete");
        return WorkflowDecision.continueWorkflow();
    }

    private String buildTeamQueryString(SportsQueryIntent intent) {
        if (intent != null && intent.hasTeams()) {
            return String.join(" ", intent.teams());
        }
        return "sports";
    }

    private String formatSearchResults(TavilySearchResponse response) {
        if (response == null) {
            return "No results found.\n";
        }

        StringBuilder builder = new StringBuilder();

        if (response.answer() != null && !response.answer().isBlank()) {
            builder.append("Summary: ").append(response.answer()).append("\n\n");
        }

        if (response.results() != null) {
            for (var result : response.results()) {
                builder.append("Title: ").append(result.title()).append("\n");
                builder.append("URL: ").append(result.url()).append("\n");
                builder.append("Content: ").append(result.content()).append("\n\n");
            }
        }

        return builder.isEmpty() ? "No results found.\n" : builder.toString();
    }
}
