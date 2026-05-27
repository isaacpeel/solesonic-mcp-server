package com.solesonic.a2a.agent.jira.node;

import com.solesonic.a2a.agent.jira.JiraState;
import com.solesonic.a2a.agent.model.JiraIssueCreatePayload;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Component
public class AssembleJiraPayloadNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(AssembleJiraPayloadNode.class);

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            boolean assigneeNotResolved = state.assigneeNotResolved().orElse(false);

            if (assigneeNotResolved) {
                log.debug("Payload assembly skipped — assignee was not resolved");
                return completedFuture(Map.of());
            }

            String summary = state.storySummary().orElseThrow(() ->
                    new IllegalStateException("storySummary is required"));
            String description = state.detailedDescription().orElseThrow(() ->
                    new IllegalStateException("detailedDescription is required"));
            List<String> acceptanceCriteria = state.acceptanceCriteria().orElse(List.of());

            JiraIssueCreatePayload payload = new JiraIssueCreatePayload(
                    summary,
                    description,
                    acceptanceCriteria,
                    state.assigneeLookupResult().orElse(null)
            );

            log.info("Assembled Jira payload: summary={}", summary);

            return completedFuture(Map.of(JiraState.FINAL_PAYLOAD, payload));
        } catch (Exception exception) {
            log.error("Failed to assemble Jira payload", exception);
            return failedFuture(exception);
        }
    }
}
