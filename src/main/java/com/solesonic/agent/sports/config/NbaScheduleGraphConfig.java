package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.SearchCurrentScheduleNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class NbaScheduleGraphConfig {

    @Bean
    public CompiledGraph<SportsState> nbaScheduleGraph(
            SearchCurrentScheduleNode searchCurrentScheduleNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.SEARCH_SCHEDULE, searchCurrentScheduleNode)
                .addNode(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)
                .addEdge(START, NbaAgentGraphConfig.SEARCH_SCHEDULE)
                .addEdge(NbaAgentGraphConfig.SEARCH_SCHEDULE, NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS)
                .addEdge(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, END)
                .compile();
    }
}
