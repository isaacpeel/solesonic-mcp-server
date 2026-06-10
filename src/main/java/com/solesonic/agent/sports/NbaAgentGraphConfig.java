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
    public static final String SEARCH_NEWS = "searchNews";
    public static final String SEARCH_STATS = "searchStats";
    public static final String SYNTHESIZE_ANALYSIS = "synthesizeAnalysis";

    private static final String SCHEDULE = "schedule";
    private static final String STANDINGS = "standings";
    private static final String NEWS_ONLY = "newsOnly";
    private static final String STATS_ONLY = "statsOnly";
    private static final String GAME_PREVIEW_NO_TEAMS = "gamePreviewNoTeams";
    private static final String FULL = "full";
    private static final String EXTRACT_TEAMS = "extractTeams";
    private static final String SYNTHESIZE = "synthesize";

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

        return new StateGraph<>(SportsState::new)
                .addNode(PARSE_SPORTS_INTENT, parseSportsIntentNode)
                .addNode(RESOLVE_ESPN_TEAM_URLS, resolveEspnTeamUrlsNode)
                .addNode(FETCH_ESPN_ROSTER, fetchEspnRosterNode)
                .addNode(FETCH_ESPN_STANDINGS, fetchEspnStandingsNode)
                .addNode(EXTRACT_TEAMS_FROM_SCHEDULE, extractTeamsFromScheduleNode)
                .addNode(SEARCH_SCHEDULE, searchCurrentScheduleNode)
                .addNode(SEARCH_NEWS, searchSportsNewsNode)
                .addNode(SEARCH_STATS, searchStatisticsNode)
                .addNode(SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)

                .addEdge(START, PARSE_SPORTS_INTENT)

                // Route from intent: direct routes skip roster pipeline; roster routes go through schedule or resolve
                .addConditionalEdges(
                        PARSE_SPORTS_INTENT,
                        edge_async(NbaAgentGraphConfig::routeByIntent),
                        Map.of(
                                SCHEDULE, SEARCH_SCHEDULE,
                                STANDINGS, FETCH_ESPN_STANDINGS,
                                NEWS_ONLY, SEARCH_NEWS,
                                STATS_ONLY, SEARCH_STATS,
                                GAME_PREVIEW_NO_TEAMS, SEARCH_SCHEDULE,
                                FULL, RESOLVE_ESPN_TEAM_URLS
                        ))

                // STANDINGS path
                .addEdge(FETCH_ESPN_STANDINGS, SYNTHESIZE_ANALYSIS)

                // Direct news/stats paths (no roster)
                .addEdge(SEARCH_NEWS, SYNTHESIZE_ANALYSIS)
                .addEdge(SEARCH_STATS, SYNTHESIZE_ANALYSIS)

                // SEARCH_SCHEDULE routes conditionally:
                //   - schedule-only intent → directly to synthesis
                //   - game-preview-no-teams → extract teams then fetch roster
                .addConditionalEdges(
                        SEARCH_SCHEDULE,
                        edge_async(NbaAgentGraphConfig::routeAfterScheduleSearch),
                        Map.of(
                                EXTRACT_TEAMS, EXTRACT_TEAMS_FROM_SCHEDULE,
                                SYNTHESIZE, SYNTHESIZE_ANALYSIS
                        ))

                // GAME_PREVIEW_NO_TEAMS path: extract teams from schedule → roster
                .addEdge(EXTRACT_TEAMS_FROM_SCHEDULE, FETCH_ESPN_ROSTER)

                // FULL path: resolve team URLs → roster
                .addEdge(RESOLVE_ESPN_TEAM_URLS, FETCH_ESPN_ROSTER)

                // After roster fetch: fan out to news and stats independently (natural graph parallelism)
                .addEdge(FETCH_ESPN_ROSTER, SEARCH_NEWS)
                .addEdge(FETCH_ESPN_ROSTER, SEARCH_STATS)

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

        boolean isNewsOnly = questionTypes.stream().allMatch(type ->
                type == SportsQuestionType.GENERAL_NEWS
                        || type == SportsQuestionType.TRADE_NEWS
                        || type == SportsQuestionType.INJURY_REPORT
                        || type == SportsQuestionType.DRAFT
                        || type == SportsQuestionType.COACHING);
        if (isNewsOnly) {
            return NEWS_ONLY;
        }

        boolean isStatsOnly = questionTypes.stream().allMatch(type ->
                type == SportsQuestionType.STATISTICS
                        || type == SportsQuestionType.HISTORICAL);
        if (isStatsOnly) {
            return STATS_ONLY;
        }

        boolean requiresRoster = questionTypes.contains(SportsQuestionType.GAME_PREVIEW)
                || questionTypes.contains(SportsQuestionType.PLAYER_ANALYSIS);

        if (requiresRoster && intent.teams().isEmpty()) {
            return GAME_PREVIEW_NO_TEAMS;
        }

        return FULL;
    }

    private static String routeAfterScheduleSearch(SportsState state) {
        SportsQueryIntent intent = state.sportsQueryIntent().orElse(null);
        boolean needsTeamExtraction = intent != null && (
                intent.questionTypes().contains(SportsQuestionType.GAME_PREVIEW)
                        || intent.questionTypes().contains(SportsQuestionType.PLAYER_ANALYSIS));
        return needsTeamExtraction ? EXTRACT_TEAMS : SYNTHESIZE;
    }
}
