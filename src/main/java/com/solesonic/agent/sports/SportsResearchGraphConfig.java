package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.model.SportsQuestionType;
import com.solesonic.agent.sports.node.FetchEspnRosterNode;
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
public class SportsResearchGraphConfig {

    public static final String PARSE_SPORTS_INTENT = "parseSportsIntent";
    public static final String RESOLVE_ESPN_TEAM_URLS = "resolveEspnTeamUrls";
    public static final String FETCH_ESPN_ROSTER = "fetchEspnRoster";
    public static final String PARALLEL_SEARCH = "parallelSearch";
    public static final String SYNTHESIZE_ANALYSIS = "synthesizeAnalysis";
    public static final String SCHEDULE = "schedule";
    public static final String STANDINGS = "standings";
    public static final String FULL = "full";

    @Bean
    public CompiledGraph<SportsState> sportsResearchGraph(
            ParseSportsIntentNode parseSportsIntentNode,
            ResolveEspnTeamUrlsNode resolveEspnTeamUrlsNode,
            FetchEspnRosterNode fetchEspnRosterNode,
            SearchCurrentScheduleNode searchCurrentScheduleNode,
            SearchSportsNewsNode searchSportsNewsNode,
            SearchStatisticsNode searchStatisticsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        List<AsyncNodeActionWithConfig<SportsState>> searchActions = List.of(
                searchCurrentScheduleNode,
                AsyncNodeActionWithConfig.of(searchSportsNewsNode),
                AsyncNodeActionWithConfig.of(searchStatisticsNode)
        );

        return new StateGraph<>(SportsState::new)
                .addNode(PARSE_SPORTS_INTENT, parseSportsIntentNode)
                .addNode(RESOLVE_ESPN_TEAM_URLS, resolveEspnTeamUrlsNode)
                .addNode(FETCH_ESPN_ROSTER, fetchEspnRosterNode)
                .addNode(PARALLEL_SEARCH, new ParallelNode.AsyncParallelNodeAction<>(PARALLEL_SEARCH, searchActions, Map.of()))
                .addNode(SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)

                .addEdge(START, PARSE_SPORTS_INTENT)

                .addConditionalEdges(
                        PARSE_SPORTS_INTENT,
                        edge_async(SportsResearchGraphConfig::routeByIntent),
                        Map.of(
                                SCHEDULE,
                                PARALLEL_SEARCH,
                                STANDINGS,
                                SYNTHESIZE_ANALYSIS,
                                FULL,
                                RESOLVE_ESPN_TEAM_URLS
                        ))

                .addEdge(RESOLVE_ESPN_TEAM_URLS, FETCH_ESPN_ROSTER)
                .addEdge(FETCH_ESPN_ROSTER, PARALLEL_SEARCH)
                .addEdge(PARALLEL_SEARCH, SYNTHESIZE_ANALYSIS)
                .addEdge(SYNTHESIZE_ANALYSIS, END)
                .compile();
    }

    private static String routeByIntent(SportsState state) {
        List<SportsQuestionType> sportsQuestionTypes = state.sportsQueryIntent()
                .map(SportsQueryIntent::questionTypes)
                .orElse(List.of());

        if (sportsQuestionTypes.stream().allMatch(type -> type == SportsQuestionType.SCHEDULE_LOOKUP)) {
            return SCHEDULE;
        }

        if (sportsQuestionTypes.stream().allMatch(type -> type == SportsQuestionType.STANDINGS)) {
            return STANDINGS;
        }

        return FULL;
    }
}
