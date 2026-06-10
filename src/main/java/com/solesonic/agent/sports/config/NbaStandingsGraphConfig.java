package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.FetchEspnStandingsNode;
import com.solesonic.agent.sports.node.SynthesizeSportsAnalysisNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

@Configuration
public class NbaStandingsGraphConfig {

    @Bean
    public CompiledGraph<SportsState> nbaStandingsGraph(
            FetchEspnStandingsNode fetchEspnStandingsNode,
            SynthesizeSportsAnalysisNode synthesizeSportsAnalysisNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.FETCH_ESPN_STANDINGS, fetchEspnStandingsNode)
                .addNode(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, synthesizeSportsAnalysisNode)
                .addEdge(START, NbaAgentGraphConfig.FETCH_ESPN_STANDINGS)
                .addEdge(NbaAgentGraphConfig.FETCH_ESPN_STANDINGS, NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS)
                .addEdge(NbaAgentGraphConfig.SYNTHESIZE_ANALYSIS, END)
                .compile();
    }
}
