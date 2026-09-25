package com.solesonic.agent.checkpoint;

import com.solesonic.agent.agile.AgileState;
import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.nba.SportsState;
import com.solesonic.agent.state.IdentifiedAgentState;
import com.solesonic.mcp.security.identity.CallerIdentity;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.mockito.Mockito.mock;

/**
 * A graph compiled with a checkpoint saver deep-copies its state at every step using the graph's
 * own serializer — Java serialization by default — so every value carried in graph state must be
 * {@link java.io.Serializable}, independently of the serializer the saver writes to Redis with.
 */
class CheckpointedStateCloningTest {

    private static final CallerIdentity CALLER = new CallerIdentity(UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"));

    @Test
    void aCheckpointedGraphRunsWithTheCallerInItsState() throws Exception {
        CompiledGraph<AgileState> graph = new StateGraph<>(AgileState::new)
                .addNode("step", node_async(_ -> Map.of(AgileState.USER_MESSAGE, "done")))
                .addEdge(START, "step")
                .addEdge("step", END)
                .compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());

        GraphRunner graphRunner = new GraphRunner(mock(BaseCheckpointSaver.class));

        NodeOutput<AgileState> finalOutput = graphRunner.run(graph,
                GraphInput.args(Map.of(AgileState.CALLER_IDENTITY, CALLER)),
                new GraphThread("test", "run-1").runnableConfig(),
                _ -> {});

        assertThat(finalOutput.isEND()).isTrue();
        assertThat(finalOutput.state().callerIdentity()).contains(CALLER);
    }

    @Test
    void everyGraphStateClonesTheCallerWithTheDefaultSerializer() throws Exception {
        Map<String, Object> data = Map.of(IdentifiedAgentState.CALLER_IDENTITY, CALLER);

        assertThat(new ObjectStreamStateSerializer<>(AgileState::new).cloneObject(data).callerIdentity()).contains(CALLER);
        assertThat(new ObjectStreamStateSerializer<>(JiraState::new).cloneObject(data).callerIdentity()).contains(CALLER);
        assertThat(new ObjectStreamStateSerializer<>(SportsState::new).cloneObject(data).callerIdentity()).contains(CALLER);
    }
}
