package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.AssigneeLookupResult;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

class AwaitAssigneeSelectionNodeTest {

    private static final String NODE_ID = "awaitAssigneeSelection";

    private final AwaitAssigneeSelectionNode node = new AwaitAssigneeSelectionNode();

    @Test
    void interrupts_whenAssigneeNotResolvedAndNoLookupResult() {
        JiraState state = new JiraState(Map.of(JiraState.ASSIGNEE_NOT_RESOLVED, true));

        Optional<InterruptionMetadata<JiraState>> interruption = node.interrupt(NODE_ID, state, RunnableConfig.empty());

        assertThat(interruption).isPresent();
        assertThat(interruption.get().nodeId()).isEqualTo(NODE_ID);
    }

    @Test
    void doesNotInterrupt_whenAssigneeAlreadyResolved() {
        AssigneeLookupResult lookupResult = new AssigneeLookupResult(true, "acc-1", "RESOLVED", "John");
        JiraState state = new JiraState(Map.of(
                JiraState.ASSIGNEE_NOT_RESOLVED, true,
                JiraState.ASSIGNEE_LOOKUP_RESULT, lookupResult
        ));

        assertThat(node.interrupt(NODE_ID, state, RunnableConfig.empty())).isEmpty();
    }

    @Test
    void doesNotInterrupt_whenNeverMarkedUnresolved() {
        JiraState state = new JiraState(Map.of());

        assertThat(node.interrupt(NODE_ID, state, RunnableConfig.empty())).isEmpty();
    }

    @Test
    void apply_isANoOp() throws Exception {
        Map<String, Object> updates = node.apply(new JiraState(Map.of())).get();

        assertThat(updates).isEmpty();
    }

    @Test
    void graph_pausesForAnUnresolvedAssignee_andResumesWithTheChosenOne() throws Exception {
        MemorySaver checkpointSaver = new MemorySaver();

        var graph = new StateGraph<>(JiraState::new)
                .addNode(NODE_ID, node)
                .addEdge(START, NODE_ID)
                .addEdge(NODE_ID, END)
                .compile(CompileConfig.builder().checkpointSaver(checkpointSaver).build());

        RunnableConfig config = RunnableConfig.builder().threadId("test-thread").build();

        NodeOutput<JiraState> paused = graph.invokeFinal(
                        GraphInput.args(Map.of(JiraState.ASSIGNEE_NOT_RESOLVED, true)), config)
                .orElseThrow();

        assertThat(paused.isEND()).isFalse();
        assertThat(checkpointSaver.get(config)).isPresent();

        AssigneeLookupResult chosen = new AssigneeLookupResult(true, "acc-1", "USER_SELECTED", "Isaac");

        NodeOutput<JiraState> completed = graph.invokeFinal(
                        GraphInput.resume(Map.of(
                                JiraState.ASSIGNEE_LOOKUP_RESULT, chosen,
                                JiraState.ASSIGNEE_NOT_RESOLVED, false
                        )), config)
                .orElseThrow();

        assertThat(completed.isEND()).isTrue();
        assertThat(completed.state().assigneeLookupResult()).contains(chosen);
    }
}
