package com.solesonic.agent.agile;

import com.solesonic.agent.agile.node.AssessOperationScopeNode;
import com.solesonic.agent.agile.node.ListBoardsNode;
import com.solesonic.agent.agile.node.ParseAgileIntentNode;
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
public class AgileGraph {

    public static final String PARSE_AND_FETCH = "parseAndFetch";
    public static final String ASSESS_SCOPE = "assessOperationScope";
    public static final String ROUTE_TRANSITION = "transition";
    public static final String ROUTE_QUERY = "query";

    @Bean
    public CompiledGraph<AgileState> agileResearchGraph(
            ParseAgileIntentNode parseAgileIntentNode,
            ListBoardsNode listBoardsNode,
            AssessOperationScopeNode assessOperationScopeNode
    ) throws GraphStateException {

        List<AsyncNodeActionWithConfig<AgileState>> parallelActions = List.of(
                AsyncNodeActionWithConfig.of(parseAgileIntentNode),
                AsyncNodeActionWithConfig.of(listBoardsNode)
        );

        ParallelNode.AsyncParallelNodeAction<AgileState> agileStateAsyncParallelNodeAction = new ParallelNode.AsyncParallelNodeAction<>(PARSE_AND_FETCH, parallelActions, Map.of());

        return new StateGraph<>(AgileState::new)
                .addNode(PARSE_AND_FETCH, agileStateAsyncParallelNodeAction)
                .addNode(ASSESS_SCOPE, assessOperationScopeNode)
                .addEdge(START, PARSE_AND_FETCH)
                .addConditionalEdges(
                        PARSE_AND_FETCH,
                        edge_async(AgileGraph::routeByQueryType),
                        Map.of(
                                ROUTE_TRANSITION, ASSESS_SCOPE,
                                ROUTE_QUERY, END
                        ))

                .addEdge(ASSESS_SCOPE, END)
                .compile();
    }

    private static String routeByQueryType(AgileState state) {
        return state.agileQueryResult()
                .filter(AgileQueryIntent::isTransitionQuery)
                .map(ignored -> ROUTE_TRANSITION)
                .orElse(ROUTE_QUERY);
    }
}
