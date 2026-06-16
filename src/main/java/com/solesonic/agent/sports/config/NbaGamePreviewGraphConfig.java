package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.ExtractTeamsFromScheduleNode;
import com.solesonic.agent.sports.node.SearchCurrentScheduleNode;
import com.solesonic.agent.sports.node.SearchSportsNewsNode;
import com.solesonic.agent.sports.node.SearchStatisticsNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class NbaGamePreviewGraphConfig {

    private static final String FETCH_ROSTER = "fetchRoster";

    @Bean
    public CompiledGraph<SportsState> nbaGamePreviewGraph(
            SearchCurrentScheduleNode searchCurrentScheduleNode,
            ExtractTeamsFromScheduleNode extractTeamsFromScheduleNode,
            @Qualifier("nbaRosterGraph") CompiledGraph<SportsState> nbaRosterGraph,
            SearchSportsNewsNode searchSportsNewsNode,
            SearchStatisticsNode searchStatisticsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.SEARCH_SCHEDULE, searchCurrentScheduleNode)
                .addNode(NbaAgentGraphConfig.EXTRACT_TEAMS_FROM_SCHEDULE, extractTeamsFromScheduleNode)
                .addNode(FETCH_ROSTER, nbaRosterGraph)
                .addNode(NbaAgentGraphConfig.SEARCH_NEWS, searchSportsNewsNode)
                .addNode(NbaAgentGraphConfig.SEARCH_STATS, searchStatisticsNode)
                .addNode(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)
                .addEdge(START, NbaAgentGraphConfig.SEARCH_SCHEDULE)
                .addEdge(NbaAgentGraphConfig.SEARCH_SCHEDULE, NbaAgentGraphConfig.EXTRACT_TEAMS_FROM_SCHEDULE)
                .addEdge(NbaAgentGraphConfig.EXTRACT_TEAMS_FROM_SCHEDULE, FETCH_ROSTER)
                .addEdge(FETCH_ROSTER, NbaAgentGraphConfig.SEARCH_NEWS)
                .addEdge(NbaAgentGraphConfig.SEARCH_NEWS, NbaAgentGraphConfig.SEARCH_STATS)
                .addEdge(NbaAgentGraphConfig.SEARCH_STATS, NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS)
                .addEdge(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, END)
                .compile();
    }
}
