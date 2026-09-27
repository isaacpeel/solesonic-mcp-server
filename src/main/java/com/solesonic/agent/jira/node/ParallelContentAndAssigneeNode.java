package com.solesonic.agent.jira.node;

import com.solesonic.agent.jira.JiraState;
import com.solesonic.mcp.exception.ToolFailures;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Runs the description->summary->acceptance-criteria chain and assignee resolution concurrently.
 * LangGraph4j's native fan-out requires parallel branches to reconverge after a single hop, which
 * this diamond (a 3-hop chain fanned out against a 1-hop branch) does not satisfy, so the two
 * branches are composed here with plain {@link CompletableFuture}s instead of graph edges.
 */
@Component
public class ParallelContentAndAssigneeNode implements AsyncNodeAction<JiraState> {

    private static final Logger log = LoggerFactory.getLogger(ParallelContentAndAssigneeNode.class);

    private static final String OPERATION = "Generating the story content and resolving the assignee";

    private final GenerateDetailedDescriptionNode generateDetailedDescriptionNode;
    private final GenerateStorySummaryNode generateStorySummaryNode;
    private final GenerateAcceptanceCriteriaNode generateAcceptanceCriteriaNode;
    private final ResolveAssigneeNode resolveAssigneeNode;

    public ParallelContentAndAssigneeNode(
            GenerateDetailedDescriptionNode generateDetailedDescriptionNode,
            GenerateStorySummaryNode generateStorySummaryNode,
            GenerateAcceptanceCriteriaNode generateAcceptanceCriteriaNode,
            ResolveAssigneeNode resolveAssigneeNode) {
        this.generateDetailedDescriptionNode = generateDetailedDescriptionNode;
        this.generateStorySummaryNode = generateStorySummaryNode;
        this.generateAcceptanceCriteriaNode = generateAcceptanceCriteriaNode;
        this.resolveAssigneeNode = resolveAssigneeNode;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(JiraState state) {
        try {
            log.info("Fanning out: generating story content and resolving the assignee concurrently");

            CompletableFuture<Map<String, Object>> assigneeFuture = CompletableFuture.supplyAsync(() -> resolveAssigneeNode.apply(state).join());

            CompletableFuture<Map<String, Object>> contentFuture = generateDetailedDescriptionNode.apply(state)
                    .thenCompose(descriptionUpdate -> {
                        JiraState afterDescription = withUpdates(state, descriptionUpdate);

                        return generateStorySummaryNode.apply(afterDescription)
                                .thenCompose(summaryUpdate -> {
                                    Map<String, Object> afterSummaryUpdates = merge(descriptionUpdate, summaryUpdate);
                                    JiraState afterSummary = withUpdates(state, afterSummaryUpdates);

                                    return generateAcceptanceCriteriaNode.apply(afterSummary)
                                            .thenApply(criteriaUpdate -> merge(afterSummaryUpdates, criteriaUpdate));
                                });
                    });

            return contentFuture.thenCombine(assigneeFuture, ParallelContentAndAssigneeNode::merge);
        } catch (Exception exception) {
            log.error("Failed to fan out story content generation and assignee resolution", exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }

    private static JiraState withUpdates(JiraState base, Map<String, Object> updates) {
        return new JiraState(merge(base.data(), updates));
    }

    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(updates);
        return merged;
    }
}
