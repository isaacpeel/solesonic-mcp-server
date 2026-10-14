package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageRoutingProperties;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import com.solesonic.mcp.exception.ToolFailures;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Turns the router's pick into one that is safe to run, with no LLM involved.
 *
 * <p>A missing or unknown workflow name falls back to the configured default workflow, or to the
 * first candidate when the default is not among the candidates. The fallback is noted in the
 * rationale, so the tool result says when one happened. The prompt is not touched here: it is
 * written afterwards by {@link WriteImagePromptNode}, for the workflow resolved here.
 */
@Component
public class ResolveImageWorkflowNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(ResolveImageWorkflowNode.class);

    private static final String OPERATION = "Resolving the image workflow";

    private final ImageRoutingProperties imageRoutingProperties;

    public ResolveImageWorkflowNode(ImageRoutingProperties imageRoutingProperties) {
        this.imageRoutingProperties = imageRoutingProperties;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(ImageWorkflowState imageWorkflowState) {
        try {
            List<WorkflowCandidate> candidates = imageWorkflowState.candidateWorkflows().orElse(List.of());
            ImageWorkflowSelection selection = imageWorkflowState.workflowSelection()
                    .orElse(new ImageWorkflowSelection(null, null, null));

            log.info("Resolving image workflow: router chose '{}' among {} candidate(s)",
                    selection.toolName(), candidates.size());

            if (candidates.isEmpty()) {
                throw new IllegalStateException("No image workflow accepts %d reference image(s)."
                        .formatted(imageWorkflowState.referenceImageCount().orElse(0)));
            }

            String toolName = selection.toolName();
            String rationale = selection.rationale();

            if (isCandidate(toolName, candidates)) {
                log.info("Resolved image workflow '{}' as chosen by the router", toolName);
            } else {
                String fallbackToolName = fallbackCandidate(candidates).toolName();

                String fallback = toolName == null
                        ? "No workflow was chosen, so the default workflow '%s' was used.".formatted(fallbackToolName)
                        : "The chosen workflow '%s' is not available, so the default workflow '%s' was used."
                                .formatted(toolName, fallbackToolName);

                log.info("Resolved image workflow '{}' with fallback (router chose '{}', default '{}'): {}",
                        fallbackToolName, toolName, imageRoutingProperties.defaultWorkflow(), fallback);

                toolName = fallbackToolName;
                rationale = StringUtils.isBlank(rationale) ? fallback : rationale.strip() + " " + fallback;
            }

            return completedFuture(Map.of(ImageWorkflowState.WORKFLOW_SELECTION,
                    new ImageWorkflowSelection(toolName, selection.enhancedPrompt(), rationale)));
        } catch (Exception exception) {
            log.error("Failed to resolve the image workflow", exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }

    private static boolean isCandidate(String toolName, List<WorkflowCandidate> candidates) {
        return toolName != null && candidates.stream()
                .anyMatch(workflowCandidate -> workflowCandidate.toolName().equals(toolName));
    }

    private WorkflowCandidate fallbackCandidate(List<WorkflowCandidate> candidates) {
        return candidates.stream()
                .filter(workflowCandidate -> workflowCandidate.toolName().equals(imageRoutingProperties.defaultWorkflow()))
                .findFirst()
                .orElse(candidates.getFirst());
    }
}
