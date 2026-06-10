package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.SearchSportsNewsNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class NbaNewsGraphConfig {

    @Bean
    public CompiledGraph<SportsState> nbaNewsGraph(
            SearchSportsNewsNode searchSportsNewsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.SEARCH_NEWS, searchSportsNewsNode)
                .addNode(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)
                .addEdge(START, NbaAgentGraphConfig.SEARCH_NEWS)
                .addEdge(NbaAgentGraphConfig.SEARCH_NEWS, NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS)
                .addEdge(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, END)
                .compile();
    }
}
