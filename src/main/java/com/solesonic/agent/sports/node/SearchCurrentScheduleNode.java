package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.model.espn.EspnScheduleSummary;
import com.solesonic.mcp.prompt.PromptConstants;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

@Component
public class SearchCurrentScheduleNode implements AsyncNodeActionWithConfig<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(SearchCurrentScheduleNode.class);

    private static final int SUFFICIENT_CONTENT_MIN_CHARS = 300;
    private static final String NBA = "NBA";

    private final EspnScheduleFetcher fetchEspnScheduleStep;
    private final NbaComScheduleFetcher fetchNbaComScheduleStep;
    private final TavilyScheduleFetcher searchTavilyScheduleStep;

    public SearchCurrentScheduleNode(
            EspnScheduleFetcher fetchEspnScheduleStep,
            NbaComScheduleFetcher fetchNbaComScheduleStep,
            TavilyScheduleFetcher searchTavilyScheduleStep
    ) {
        this.fetchEspnScheduleStep = fetchEspnScheduleStep;
        this.fetchNbaComScheduleStep = fetchNbaComScheduleStep;
        this.searchTavilyScheduleStep = searchTavilyScheduleStep;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state, RunnableConfig config) {
        SportsQueryIntent sportsQueryIntent = state.sportsQueryIntent().orElse(null);
        String currentDate = state.currentDateTime().orElseGet(PromptConstants::todayDate);
        String teamQuery = buildTeamQueryString(sportsQueryIntent);

        SynthesisOutputEmitter emitter = config
                .metadata(SynthesisOutputEmitter.CONFIG_KEY)
                .map(SynthesisOutputEmitter.class::cast)
                .orElseGet(SynthesisOutputEmitter::noOp);

        // Tier 1: ESPN JSON API — structured, no scraping
        emitter.emitProgress("Fetching schedule from ESPN...");
        EspnScheduleSummary espnScheduleSummary = fetchEspnScheduleStep.fetch(state);

        if (espnScheduleSummary.hasUpcomingOrLiveGames()) {
            log.info("Schedule found on ESPN — skipping NBA.com and Tavily");
            return completedFuture(Map.of(
                    SportsState.SCHEDULE_SEARCH_SUMMARY, espnScheduleSummary.toFormattedString(),
                    SportsState.ESPN_SCHEDULE_SUMMARY_OBJECT, espnScheduleSummary,
                    SportsState.SCHEDULE_ALREADY_FETCHED, true
            ));
        }

        // Tier 2: Direct NBA.com extract
        emitter.emitProgress("Checking NBA.com for schedule...");
        String nbaResult = fetchNbaComScheduleStep.fetch();

        if (isSufficient(nbaResult)) {
            log.info("Schedule found on NBA.com — skipping Tavily");
            return completedFuture(Map.of(
                    SportsState.SCHEDULE_SEARCH_SUMMARY, nbaResult,
                    SportsState.SCHEDULE_ALREADY_FETCHED, true
            ));
        }

        // Tier 3: Broad Tavily search — last resort, no domain restrictions
        log.info("ESPN and NBA.com insufficient — falling back to Tavily");
        emitter.emitProgress("Searching for schedule information...");
        String tavilyResult = searchTavilyScheduleStep.fetch(teamQuery, currentDate);
        return completedFuture(Map.of(
                SportsState.SCHEDULE_SEARCH_SUMMARY, tavilyResult,
                SportsState.SCHEDULE_ALREADY_FETCHED, true
        ));
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
