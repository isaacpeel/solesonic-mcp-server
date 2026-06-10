package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.model.SportsQuestionType;
import com.solesonic.agent.sports.node.ExtractTeamsFromScheduleNode;
import com.solesonic.agent.sports.node.FetchEspnRosterNode;
import com.solesonic.agent.sports.node.FetchEspnStandingsNode;
import com.solesonic.agent.sports.node.ParseSportsIntentNode;
import com.solesonic.agent.sports.node.ResolveEspnTeamUrlsNode;
import com.solesonic.agent.sports.node.SearchCurrentScheduleNode;
import com.solesonic.agent.sports.node.SearchSportsNewsNode;
import com.solesonic.agent.sports.node.SearchStatisticsNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.internal.node.ParallelNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Configuration
public class NbaAgentGraphConfig {

    public static final String PARSE_SPORTS_INTENT = "parseSportsIntent";
    public static final String RESOLVE_ESPN_TEAM_URLS = "resolveEspnTeamUrls";
    public static final String FETCH_ESPN_ROSTER = "fetchEspnRoster";
    public static final String FETCH_ESPN_STANDINGS = "fetchEspnStandings";
    public static final String EXTRACT_TEAMS_FROM_SCHEDULE = "extractTeamsFromSchedule";
    public static final String SEARCH_SCHEDULE = "searchSchedule";
    public static final String SEARCH_NEWS_AND_STATS = "searchNewsAndStats";
    public static final String PARALLEL_SEARCH = "parallelSearch";
    public static final String SYNTHESIZE_ANALYSIS = "synthesizeAnalysis";

    private static final String SCHEDULE = "schedule";
    private static final String STANDINGS = "standings";
    private static final String FULL = "full";
    private static final String GAME_PREVIEW_NO_TEAMS = "gamePreviewNoTeams";
    private static final String AFTER_ROSTER_FULL = "afterRosterFull";
    private static final String AFTER_ROSTER_NO_TEAMS = "afterRosterNoTeams";

    @Bean
    public CompiledGraph<SportsState> sportsResearchGraph(
            ParseSportsIntentNode parseSportsIntentNode,
            ResolveEspnTeamUrlsNode resolveEspnTeamUrlsNode,
            FetchEspnRosterNode fetchEspnRosterNode,
            FetchEspnStandingsNode fetchEspnStandingsNode,
            ExtractTeamsFromScheduleNode extractTeamsFromScheduleNode,
            SearchCurrentScheduleNode searchCurrentScheduleNode,
            SearchSportsNewsNode searchSportsNewsNode,
            SearchStatisticsNode searchStatisticsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        List<AsyncNodeActionWithConfig<SportsState>> allSearchActions = List.of(
                searchCurrentScheduleNode,
                AsyncNodeActionWithConfig.of(searchSportsNewsNode),
                AsyncNodeActionWithConfig.of(searchStatisticsNode)
        );

        List<AsyncNodeActionWithConfig<SportsState>> newsAndStatsActions = List.of(
                AsyncNodeActionWithConfig.of(searchSportsNewsNode),
                AsyncNodeActionWithConfig.of(searchStatisticsNode)
        );

        return new StateGraph<>(SportsState::new)
                .addNode(PARSE_SPORTS_INTENT, parseSportsIntentNode)
                .addNode(RESOLVE_ESPN_TEAM_URLS, resolveEspnTeamUrlsNode)
                .addNode(FETCH_ESPN_ROSTER, fetchEspnRosterNode)
                .addNode(FETCH_ESPN_STANDINGS, fetchEspnStandingsNode)
                .addNode(EXTRACT_TEAMS_FROM_SCHEDULE, extractTeamsFromScheduleNode)
                .addNode(SEARCH_SCHEDULE, searchCurrentScheduleNode)
                .addNode(PARALLEL_SEARCH, new ParallelNode.AsyncParallelNodeAction<>(PARALLEL_SEARCH, allSearchActions, Map.of()))
                .addNode(SEARCH_NEWS_AND_STATS, new ParallelNode.AsyncParallelNodeAction<>(SEARCH_NEWS_AND_STATS, newsAndStatsActions, Map.of()))
                .addNode(SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)

                .addEdge(START, PARSE_SPORTS_INTENT)

                .addConditionalEdges(
                        PARSE_SPORTS_INTENT,
                        edge_async(NbaAgentGraphConfig::routeByIntent),
                        Map.of(
                                SCHEDULE,               PARALLEL_SEARCH,
                                STANDINGS,              FETCH_ESPN_STANDINGS,
                                GAME_PREVIEW_NO_TEAMS,  SEARCH_SCHEDULE,
                                FULL,                   RESOLVE_ESPN_TEAM_URLS
                        ))

                // STANDINGS path
                .addEdge(FETCH_ESPN_STANDINGS, SYNTHESIZE_ANALYSIS)

                // GAME_PREVIEW_NO_TEAMS path: fetch schedule → extract teams → roster directly
                // NOTE: bypasses resolveEspnTeamUrls because that node overwrites RESOLVED_TEAMS with an
                // empty list when intent.teams() is empty, which would discard what extractTeamsFromSchedule set
                .addEdge(SEARCH_SCHEDULE, EXTRACT_TEAMS_FROM_SCHEDULE)
                .addEdge(EXTRACT_TEAMS_FROM_SCHEDULE, FETCH_ESPN_ROSTER)

                // FULL path: resolve URLs → roster
                .addEdge(RESOLVE_ESPN_TEAM_URLS, FETCH_ESPN_ROSTER)

                // Route after roster fetch: if schedule already in state → news+stats only; otherwise → full parallel search
                .addConditionalEdges(
                        FETCH_ESPN_ROSTER,
                        edge_async(NbaAgentGraphConfig::routeAfterRosterFetch),
                        Map.of(
                                AFTER_ROSTER_NO_TEAMS, SEARCH_NEWS_AND_STATS,
                                AFTER_ROSTER_FULL,     PARALLEL_SEARCH
                        ))

                .addEdge(PARALLEL_SEARCH, SYNTHESIZE_ANALYSIS)
                .addEdge(SEARCH_NEWS_AND_STATS, SYNTHESIZE_ANALYSIS)
                .addEdge(SYNTHESIZE_ANALYSIS, END)
                .compile();
    }

    private static String routeByIntent(SportsState state) {
        SportsQueryIntent intent = state.sportsQueryIntent().orElse(null);

        if (intent == null) {
            return FULL;
        }

        List<SportsQuestionType> questionTypes = intent.questionTypes();

        if (questionTypes.stream().allMatch(type -> type == SportsQuestionType.SCHEDULE_LOOKUP)) {
            return SCHEDULE;
        }

        if (questionTypes.stream().allMatch(type -> type == SportsQuestionType.STANDINGS)) {
            return STANDINGS;
        }

        boolean requiresRoster = questionTypes.contains(SportsQuestionType.GAME_PREVIEW)
                || questionTypes.contains(SportsQuestionType.PLAYER_ANALYSIS);

        if (requiresRoster && intent.teams().isEmpty()) {
            return GAME_PREVIEW_NO_TEAMS;
        }

        return FULL;
    }

    private static String routeAfterRosterFetch(SportsState state) {
        // GAME_PREVIEW_NO_TEAMS path stored schedule before entering this node; FULL path has not
        return state.scheduleSearchSummary().isPresent() ? AFTER_ROSTER_NO_TEAMS : AFTER_ROSTER_FULL;
    }
}
