package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.agent.model.JiraIssueCreatePayload;
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
    public CompletableFuture<Map<String, Object>> apply(JiraState jiraState) {
        try {
            boolean assigneeNotResolved = jiraState.assigneeNotResolved().orElse(false);

            if (assigneeNotResolved) {
                log.debug("Payload assembly skipped — assignee was not resolved");
                return completedFuture(Map.of());
            }

            String summary = jiraState.storySummary().orElseThrow(() -> new IllegalStateException("storySummary is required"));

            String description = jiraState.detailedDescription().orElseThrow(() -> new IllegalStateException("detailedDescription is required"));

            List<String> acceptanceCriteria = jiraState.acceptanceCriteria().orElse(List.of());

            JiraIssueCreatePayload payload = new JiraIssueCreatePayload(
                    summary,
                    description,
                    acceptanceCriteria,
                    jiraState.assigneeLookupResult().orElse(null)
            );

            log.info("Assembled Jira payload: summary={}", summary);

            return completedFuture(Map.of(JiraState.FINAL_PAYLOAD, payload));
        } catch (Exception exception) {
            log.error("Failed to assemble Jira payload", exception);
            return failedFuture(exception);
        }
    }
}
