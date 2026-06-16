package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.SearchStatisticsNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class NbaStatsGraphConfig {

    @Bean
    public CompiledGraph<SportsState> nbaStatsGraph(
            SearchStatisticsNode searchStatisticsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.SEARCH_STATS, searchStatisticsNode)
                .addNode(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)
                .addEdge(START, NbaAgentGraphConfig.SEARCH_STATS)
                .addEdge(NbaAgentGraphConfig.SEARCH_STATS, NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS)
                .addEdge(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, END)
                .compile();
    }
}
