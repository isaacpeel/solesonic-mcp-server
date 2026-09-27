package com.solesonic.agent.checkpoint;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs a checkpointed graph to completion — the one way entry points execute graphs.
 * <p>
 * LangGraph4j releases a run's checkpoint thread when the graph reaches its end, but not when a node
 * fails; left alone, a failed run's checkpoints would linger and a later call in the same
 * conversation could mistake them for a paused run and resume it. This runner discards the run's
 * checkpoints on failure, so every run either completes and is released, pauses on an interrupt
 * and is kept for resumption, or fails and is discarded.
 */
@Component
public class GraphRunner {
    private static final Logger log = LoggerFactory.getLogger(GraphRunner.class);

    private final BaseCheckpointSaver graphCheckpointSaver;

    public GraphRunner(BaseCheckpointSaver graphCheckpointSaver) {
        this.graphCheckpointSaver = graphCheckpointSaver;
    }

    /**
     * Streams the graph, handing every node output to {@code outputListener}, and returns the last
     * output: the graph's end or, per {@link NodeOutput#isEND()}, the point where an interrupt paused it.
     */
    public <State extends AgentState> NodeOutput<State> run(CompiledGraph<State> graph,
                                                            GraphInput input,
                                                            RunnableConfig config,
                                                            Consumer<NodeOutput<State>> outputListener) {
        return runAsync(graph, input, config, outputListener).join();
    }

    public <State extends AgentState> CompletableFuture<NodeOutput<State>> runAsync(CompiledGraph<State> graph,
                                                                                    GraphInput input,
                                                                                    RunnableConfig config,
                                                                                    Consumer<NodeOutput<State>> outputListener) {
        AtomicReference<NodeOutput<State>> lastOutput = new AtomicReference<>();

        return graph.stream(input, config)
                .forEachAsync(output -> {
                    lastOutput.set(output);
                    outputListener.accept(output);
                })
                .whenComplete((_, failure) -> {
                    if (failure != null) {
                        discardFailedRun(config, failure);
                    }
                })
                .thenApply(_ -> lastOutput.get());
    }

    /**
     * Abandons a paused run, so the conversation's next call starts fresh instead of resuming it.
     */
    public void discard(RunnableConfig config) {
        try {
            graphCheckpointSaver.release(config);
        } catch (Exception exception) {
            log.warn("Failed to discard the checkpoints for graph thread {}", config.threadId().orElse(null), exception);
        }
    }

    private void discardFailedRun(RunnableConfig config, Throwable failure) {
        try {
            graphCheckpointSaver.releaseOnError(config, failure);
        } catch (Exception exception) {
            log.warn("Failed to discard the checkpoints of failed graph thread {}", config.threadId().orElse(null), exception);
        }
    }
}
