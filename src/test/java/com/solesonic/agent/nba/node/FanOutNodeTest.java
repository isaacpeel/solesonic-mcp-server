package com.solesonic.agent.nba.node;

import com.solesonic.agent.checkpoint.GraphRunner;
import com.solesonic.agent.checkpoint.GraphThread;
import com.solesonic.agent.nba.SportsState;
import com.solesonic.agent.nba.model.SportsQueryIntent;
import com.solesonic.agent.nba.model.SportsQuestionType;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FanOutNodeTest {

    private CompiledGraph<SportsState> nbaScheduleGraph;
    private CompiledGraph<SportsState> nbaStandingsGraph;
    private GraphRunner graphRunner;
    private FanOutNode fanOutNode;

    @BeforeEach
    void setUp() {
        nbaScheduleGraph = mockGraph();
        nbaStandingsGraph = mockGraph();
        graphRunner = mock(GraphRunner.class);

        fanOutNode = new FanOutNode(nbaScheduleGraph, nbaStandingsGraph, mockGraph(), mockGraph(), mockGraph(), mockGraph(), graphRunner);
    }

    private static CompiledGraph<SportsState> mockGraph() {
        return mock();
    }

    private void respondFrom(CompiledGraph<SportsState> subGraph, String analysis) {
        NodeOutput<SportsState> finalOutput = NodeOutput.of(END, new SportsState(Map.of(SportsState.FINAL_ANALYSIS, analysis)));

        when(graphRunner.runAsync(eq(subGraph), any(GraphInput.class), any(RunnableConfig.class),
                ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(finalOutput));
    }

    @Test
    void eachSubGraphRunsOnItsOwnChildThread() throws Exception {
        respondFrom(nbaScheduleGraph, "schedule analysis");
        respondFrom(nbaStandingsGraph, "standings analysis");

        SportsState state = new SportsState(Map.of(SportsState.SPORTS_QUERY_INTENT, new SportsQueryIntent(
                List.of(SportsQuestionType.SCHEDULE_LOOKUP, SportsQuestionType.STANDINGS), List.of(), List.of(), null)));
        RunnableConfig parentConfig = new GraphThread("nba", "run-1").runnableConfig();

        Map<String, Object> updates = fanOutNode.apply(state, parentConfig).get();

        assertThat(updates).containsEntry(SportsState.SUB_GRAPH_RESULTS, Map.of(
                "schedule", "schedule analysis",
                "standings", "standings analysis"));
        assertThat(childThreadIdFor(nbaScheduleGraph)).contains("nba:run-1:schedule");
        assertThat(childThreadIdFor(nbaStandingsGraph)).contains("nba:run-1:standings");
    }

    private Optional<String> childThreadIdFor(CompiledGraph<SportsState> subGraph) {
        ArgumentCaptor<RunnableConfig> childConfigCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(graphRunner).runAsync(eq(subGraph), any(GraphInput.class), childConfigCaptor.capture(),
                ArgumentMatchers.any());
        return childConfigCaptor.getValue().threadId();
    }
}
