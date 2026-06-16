package com.solesonic.agent.sports.config;

import com.solesonic.agent.sports.NbaAgentGraphConfig;
import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.node.FetchEspnRosterNode;
import com.solesonic.agent.sports.node.ResolveEspnTeamUrlsNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * Data-only sub-graph: resolves ESPN team URLs and fetches roster data.
 * No synthesis — composed into NbaGamePreviewGraph and NbaPlayerGraph.
 */
@Configuration
public class NbaRosterGraphConfig {

    @Bean
    public CompiledGraph<SportsState> nbaRosterGraph(
            ResolveEspnTeamUrlsNode resolveEspnTeamUrlsNode,
            FetchEspnRosterNode fetchEspnRosterNode
    ) throws GraphStateException {

        return new StateGraph<>(SportsState::new)
                .addNode(NbaAgentGraphConfig.RESOLVE_ESPN_TEAM_URLS, resolveEspnTeamUrlsNode)
                .addNode(NbaAgentGraphConfig.FETCH_ESPN_ROSTER, fetchEspnRosterNode)
                .addEdge(START, NbaAgentGraphConfig.RESOLVE_ESPN_TEAM_URLS)
                .addEdge(NbaAgentGraphConfig.RESOLVE_ESPN_TEAM_URLS, NbaAgentGraphConfig.FETCH_ESPN_ROSTER)
                .addEdge(NbaAgentGraphConfig.FETCH_ESPN_ROSTER, END)
                .compile();
    }
}
