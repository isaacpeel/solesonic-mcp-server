package com.solesonic.service.comfyui;

import com.solesonic.model.comfyui.ComfyEvent;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionCached;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionStarted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionSucceeded;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyEvent.NodeProgress;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns one generation's ComfyUI events into a percentage and a message. No I/O, one instance
 * per generation.
 *
 * <p>The percentage is the share of the workflow's nodes that have run:
 * {@code (finished + current node's step fraction) / nodes to run}, where nodes to run are the
 * submitted graph's nodes minus those ComfyUI served from cache. Nodes take very different times, so
 * the bar pauses on a slow node with no step events (a model load) and moves with each sampler step;
 * the message names the node either way. The node count is an estimate: ComfyUI skips nodes no
 * output depends on, and subgraph expansion runs ids that are not in the submitted graph and are
 * ignored here.
 */
public final class ComfyProgressTracker {

    /**
     * The range between {@code ComfyUiService}'s queued (15) and downloading (90) steps.
     */
    private static final int FIRST_PERCENT = 15;
    private static final int LAST_PERCENT = 85;

    private static final String FIELD_CLASS_TYPE = "class_type";
    private static final String FIELD_META = "_meta";
    private static final String FIELD_TITLE = "title";

    public record ProgressUpdate(int percent, String message) {}

    private final String promptId;
    private final Map<String, String> nodeTitles = new HashMap<>();
    private final Set<String> nodesToRun = new HashSet<>();
    private final Set<String> finishedNodes = new HashSet<>();

    private boolean started;
    private String runningNodeId;
    private double runningNodeFraction;
    private String message = "Waiting to start";

    public ComfyProgressTracker(ObjectNode workflow, String promptId) {
        this.promptId = promptId;

        for (Map.Entry<String, JsonNode> node : workflow.properties()) {
            if (node.getValue() instanceof ObjectNode nodeDefinition) {
                nodesToRun.add(node.getKey());
                nodeTitles.put(node.getKey(), title(node.getKey(), nodeDefinition));
            }
        }
    }

    /**
     * Updates the state from one event. Empty when the event does not change what is reported:
     * another prompt's event, a queue change after this prompt started, or the end-of-prompt signal,
     * which the caller handles itself.
     */
    public Optional<ProgressUpdate> apply(ComfyEvent comfyEvent) {
        return switch (comfyEvent) {
            case QueueStatus queueStatus when !started -> queued(queueStatus);
            case ExecutionStarted executionStarted when isThisPrompt(executionStarted.promptId()) -> {
                started = true;
                yield update("Starting");
            }
            case ExecutionCached executionCached when isThisPrompt(executionCached.promptId()) -> {
                executionCached.nodeIds().forEach(nodesToRun::remove);
                yield update(message);
            }
            case NodeExecuting nodeExecuting when isThisPrompt(nodeExecuting.promptId()) && nodeExecuting.nodeId() != null -> {
                started = true;
                runningNodeId = nodeExecuting.nodeId();
                runningNodeFraction = 0;
                yield update("Running " + titleOf(nodeExecuting.nodeId()));
            }
            case NodeProgress nodeProgress when isThisPrompt(nodeProgress.promptId()) && nodeProgress.max() > 0 -> stepped(nodeProgress);
            case NodeExecuted nodeExecuted when isThisPrompt(nodeExecuted.promptId()) -> finished(nodeExecuted);
            case ExecutionSucceeded executionSucceeded when isThisPrompt(executionSucceeded.promptId()) -> {
                finishedNodes.addAll(nodesToRun);
                runningNodeId = null;
                runningNodeFraction = 0;
                yield update("Finishing");
            }
            default -> Optional.empty();
        };
    }

    private Optional<ProgressUpdate> queued(QueueStatus queueStatus) {
        int ahead = Math.max(queueStatus.queueRemaining() - 1, 0);

        return update(ahead == 0 ? "Waiting to start" : "Waiting in queue (%d ahead)".formatted(ahead));
    }

    private Optional<ProgressUpdate> stepped(NodeProgress nodeProgress) {
        started = true;

        if (nodeProgress.nodeId() != null) {
            runningNodeId = nodeProgress.nodeId();
        }

        runningNodeFraction = Math.clamp(nodeProgress.value() / nodeProgress.max(), 0.0, 1.0);

        return update("%s: step %d/%d".formatted(
                titleOf(runningNodeId),
                Math.round(nodeProgress.value()),
                Math.round(nodeProgress.max())));
    }

    private Optional<ProgressUpdate> finished(NodeExecuted nodeExecuted) {
        String nodeId = nodeExecuted.nodeId();

        if (nodeId == null || !nodesToRun.contains(nodeId)) {
            return Optional.empty();
        }

        finishedNodes.add(nodeId);

        if (nodeId.equals(runningNodeId)) {
            runningNodeId = null;
            runningNodeFraction = 0;
        }

        return update(message);
    }

    private Optional<ProgressUpdate> update(String newMessage) {
        message = newMessage;

        return Optional.of(new ProgressUpdate(percent(), message));
    }

    private int percent() {
        long finishedToRun = finishedNodes.stream().filter(nodesToRun::contains).count();
        double completed = (finishedToRun + runningNodeFraction) / Math.max(nodesToRun.size(), 1);
        double ratio = Math.clamp(completed, 0.0, 1.0);

        return FIRST_PERCENT + (int) ((LAST_PERCENT - FIRST_PERCENT) * ratio);
    }

    private boolean isThisPrompt(String eventPromptId) {
        return promptId.equals(eventPromptId);
    }

    private String titleOf(String nodeId) {
        if (nodeId == null) {
            return "a node";
        }

        return nodeTitles.getOrDefault(nodeId, "node " + nodeId);
    }

    /**
     * The editor's node title reads better than the class name, and is what the workflow's author
     * sees in ComfyUI.
     */
    private static String title(String nodeId, ObjectNode nodeDefinition) {
        JsonNode title = nodeDefinition.path(FIELD_META).path(FIELD_TITLE);

        if (title.isString() && StringUtils.isNotBlank(title.stringValue())) {
            return title.stringValue();
        }

        JsonNode classType = nodeDefinition.path(FIELD_CLASS_TYPE);

        if (classType.isString() && StringUtils.isNotBlank(classType.stringValue())) {
            return classType.stringValue();
        }

        return "node " + nodeId;
    }
}
