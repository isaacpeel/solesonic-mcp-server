package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.InterruptibleAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Pauses the graph here when {@link ResolveAssigneeNode} could not resolve an assignee on its own,
 * so {@code JiraIssueTools.createJiraStory} can elicit a choice from the user and resume the same
 * graph run with it instead of restarting story generation from scratch. Once state carries a
 * resolved assignee (either from automatic resolution or a resumed human answer), this node is a
 * pass-through.
 */
@Component
public class AwaitAssigneeSelectionNode implements AsyncNodeAction<JiraState>, InterruptibleAction<JiraState> {

    @Override
    public Optional<InterruptionMetadata<JiraState>> interrupt(String nodeId, JiraState state, RunnableConfig config) {
        boolean needsHuman = state.assigneeNotResolved().orElse(false) && state.assigneeLookupResult().isEmpty();

        if (needsHuman) {
            return Optional.of(InterruptionMetadata.builder(nodeId, state).build());
        }

        return Optional.empty();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        return CompletableFuture.completedFuture(Map.of());
    }
}
