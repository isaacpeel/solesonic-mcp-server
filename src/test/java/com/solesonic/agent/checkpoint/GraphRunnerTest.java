package com.solesonic.agent.checkpoint;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GraphRunnerTest {

    private final RunnableConfig config = new GraphThread("test", "chat-1").runnableConfig();

    private BaseCheckpointSaver graphCheckpointSaver;
    private GraphRunner graphRunner;

    @BeforeEach
    void setUp() {
        graphCheckpointSaver = mock(BaseCheckpointSaver.class);
        graphRunner = new GraphRunner(graphCheckpointSaver);
    }

    private static CompiledGraph<AgentState> twoStepGraph() throws GraphStateException {
        return new StateGraph<>(AgentState::new)
                .addNode("first", node_async(_ -> Map.of("first", "done")))
                .addNode("second", node_async(_ -> Map.of("second", "done")))
                .addEdge(START, "first")
                .addEdge("first", "second")
                .addEdge("second", END)
                .compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());
    }

    private static CompiledGraph<AgentState> failingGraph() throws GraphStateException {
        return new StateGraph<>(AgentState::new)
                .addNode("explode", node_async(_ -> {
                    throw new IllegalStateException("boom");
                }))
                .addEdge(START, "explode")
                .addEdge("explode", END)
                .compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());
    }

    @Test
    void run_streamsEveryNodeToTheListenerAndReturnsTheFinalOutput() throws Exception {
        List<String> visitedNodes = new ArrayList<>();

        NodeOutput<AgentState> finalOutput = graphRunner.run(twoStepGraph(), GraphInput.args(Map.of()), config,
                output -> visitedNodes.add(output.node()));

        assertThat(visitedNodes).containsExactly(START, "first", "second", END);
        assertThat(finalOutput.isEND()).isTrue();
        assertThat(finalOutput.state().data()).containsEntry("second", "done");
        verify(graphCheckpointSaver, never()).releaseOnError(any(), any());
    }

    @Test
    void run_whenANodeFails_discardsTheRunsCheckpointsAndRethrows() throws Exception {
        CompiledGraph<AgentState> failingGraph = failingGraph();

        assertThatThrownBy(() -> graphRunner.run(failingGraph, GraphInput.args(Map.of()), config, _ -> {}))
                .hasRootCauseMessage("boom");

        verify(graphCheckpointSaver).releaseOnError(eq(config), any(Throwable.class));
    }

    @Test
    void runAsync_whenANodeFails_discardsTheRunsCheckpoints() throws Exception {
        CompiledGraph<AgentState> failingGraph = failingGraph();

        assertThat(graphRunner.runAsync(failingGraph, GraphInput.args(Map.of()), config, _ -> {}))
                .failsWithin(Duration.ofSeconds(5));

        verify(graphCheckpointSaver).releaseOnError(eq(config), any(Throwable.class));
    }

    @Test
    void discard_releasesTheThread() throws Exception {
        graphRunner.discard(config);

        verify(graphCheckpointSaver).release(config);
    }
}
