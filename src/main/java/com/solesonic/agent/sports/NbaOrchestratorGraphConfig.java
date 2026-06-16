package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.node.FanOutNode;
import com.solesonic.agent.sports.node.MetaSynthesizeNode;
import com.solesonic.agent.sports.node.ParseSportsIntentNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Configuration
public class NbaOrchestratorGraphConfig {

    public static final String PARSE_SPORTS_INTENT = NbaAgentGraphConfig.PARSE_SPORTS_INTENT;
    public static final String FAN_OUT = "fanOut";
    public static final String META_SYNTHESIZE = "metaSynthesize";

    private static final String SCHEDULE_GRAPH = "scheduleGraph";
    private static final String STANDINGS_GRAPH = "standingsGraph";
    private static final String NEWS_GRAPH = "newsGraph";
    private static final String STATS_GRAPH = "statsGraph";
    private static final String GAME_PREVIEW_GRAPH = "gamePreviewGraph";
    private static final String PLAYER_GRAPH = "playerGraph";

    private static final String SCHEDULE = "schedule";
    private static final String STANDINGS = "standings";
    private static final String NEWS = "news";
    private static final String STATS = "stats";
    private static final String GAME_PREVIEW = "gamePreview";
    private static final String PLAYER = "player";
    private static final String MULTI = "multi";

    @Bean
    public CompiledGraph<SportsState> nbaOrchestratorGraph(
            ParseSportsIntentNode parseSportsIntentNode,
            @Qualifier("nbaScheduleGraph") CompiledGraph<SportsState> nbaScheduleGraph,
            @Qualifier("nbaStandingsGraph") CompiledGraph<SportsState> nbaStandingsGraph,
            @Qualifier("nbaNewsGraph") CompiledGraph<SportsState> nbaNewsGraph,
            @Qualifier("nbaStatsGraph") CompiledGraph<SportsState> nbaStatsGraph,
            @Qualifier("nbaGamePreviewGraph") CompiledGraph<SportsState> nbaGamePreviewGraph,
            @Qualifier("nbaPlayerGraph") CompiledGraph<SportsState> nbaPlayerGraph,
            FanOutNode fanOutNode,
            MetaSynthesizeNode metaSynthesizeNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(PARSE_SPORTS_INTENT, parseSportsIntentNode)
                .addNode(SCHEDULE_GRAPH, nbaScheduleGraph)
                .addNode(STANDINGS_GRAPH, nbaStandingsGraph)
                .addNode(NEWS_GRAPH, nbaNewsGraph)
                .addNode(STATS_GRAPH, nbaStatsGraph)
                .addNode(GAME_PREVIEW_GRAPH, nbaGamePreviewGraph)
                .addNode(PLAYER_GRAPH, nbaPlayerGraph)
                .addNode(FAN_OUT, fanOutNode)
                .addNode(META_SYNTHESIZE, metaSynthesizeNode)

                .addEdge(START, PARSE_SPORTS_INTENT)

                .addConditionalEdges(
                        PARSE_SPORTS_INTENT,
                        edge_async(NbaOrchestratorGraphConfig::routeByIntent),
                        Map.of(
                                SCHEDULE, SCHEDULE_GRAPH,
                                STANDINGS, STANDINGS_GRAPH,
                                NEWS, NEWS_GRAPH,
                                STATS, STATS_GRAPH,
                                GAME_PREVIEW, GAME_PREVIEW_GRAPH,
                                PLAYER, PLAYER_GRAPH,
                                MULTI, FAN_OUT
                        ))

                // Single-type routes: each sub-graph handles the full pipeline including synthesis
                .addEdge(SCHEDULE_GRAPH, END)
                .addEdge(STANDINGS_GRAPH, END)
                .addEdge(NEWS_GRAPH, END)
                .addEdge(STATS_GRAPH, END)
                .addEdge(GAME_PREVIEW_GRAPH, END)
                .addEdge(PLAYER_GRAPH, END)

                // Multi-type route: fan out, then meta-synthesize the combined results
                .addEdge(FAN_OUT, META_SYNTHESIZE)
                .addEdge(META_SYNTHESIZE, END)
                .compile();
    }

    private static String routeByIntent(SportsState state) {
        SportsQueryIntent intent = state.sportsQueryIntent().orElse(null);

        if (intent == null || intent.questionTypes().size() != 1) {
            return MULTI;
        }

        return switch (intent.questionTypes().getFirst()) {
            case SCHEDULE_LOOKUP -> SCHEDULE;
            case STANDINGS -> STANDINGS;
            case GENERAL_NEWS, TRADE_NEWS, INJURY_REPORT, DRAFT, COACHING -> NEWS;
            case STATISTICS, HISTORICAL -> STATS;
            case GAME_PREVIEW -> GAME_PREVIEW;
            case PLAYER_ANALYSIS -> PLAYER;
        };
    }
}
