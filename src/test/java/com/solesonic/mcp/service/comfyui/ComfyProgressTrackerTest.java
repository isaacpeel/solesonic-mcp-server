package com.solesonic.mcp.service.comfyui;

import com.solesonic.model.comfyui.ComfyEvent.ExecutionCached;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionStarted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionSucceeded;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyEvent.NodeProgress;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import com.solesonic.service.comfyui.ComfyProgressTracker;
import com.solesonic.service.comfyui.ComfyProgressTracker.ProgressUpdate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComfyProgressTrackerTest {

    private static final String PROMPT_ID = "abc-123";

    private static final String FIVE_NODE_WORKFLOW = """
            {"4": {"class_type": "CheckpointLoaderSimple", "inputs": {}, "_meta": {"title": "Load Checkpoint"}},
             "6": {"class_type": "CLIPTextEncode", "inputs": {}},
             "3": {"class_type": "KSampler", "inputs": {}},
             "8": {"class_type": "VAEDecode", "inputs": {}},
             "9": {"class_type": "SaveImage", "inputs": {}, "_meta": {"title": " "}}}""";

    private static final String TWO_SAMPLER_WORKFLOW = """
            {"1": {"class_type": "KSampler", "inputs": {}, "_meta": {"title": "First pass"}},
             "2": {"class_type": "KSampler", "inputs": {}, "_meta": {"title": "Second pass"}}}""";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void apply_queueStatusBeforeStart_reportsHowManyPromptsAreAhead() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        assertThat(comfyProgressTracker.apply(new QueueStatus(3)))
                .contains(new ProgressUpdate(15, "Waiting in queue (2 ahead)"));
        assertThat(comfyProgressTracker.apply(new QueueStatus(1)))
                .contains(new ProgressUpdate(15, "Waiting to start"));
    }

    @Test
    void apply_queueStatusAfterStart_isIgnored() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);
        comfyProgressTracker.apply(new ExecutionStarted(PROMPT_ID));

        assertThat(comfyProgressTracker.apply(new QueueStatus(4))).isEmpty();
    }

    @Test
    void apply_nodesAndSteps_advanceByShareOfNodesRun() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        assertThat(comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "4")))
                .contains(new ProgressUpdate(15, "Running Load Checkpoint"));
        assertThat(comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "4")))
                .contains(new ProgressUpdate(29, "Running Load Checkpoint"));

        comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "6"));
        comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "6"));

        assertThat(comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "3")))
                .contains(new ProgressUpdate(43, "Running KSampler"));
        assertThat(comfyProgressTracker.apply(new NodeProgress(PROMPT_ID, "3", 15, 30)))
                .contains(new ProgressUpdate(50, "KSampler: step 15/30"));

        comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "3"));

        assertThat(comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "9")))
                .map(ProgressUpdate::message)
                .contains("Running SaveImage");
        assertThat(comfyProgressTracker.apply(new ExecutionSucceeded(PROMPT_ID)))
                .contains(new ProgressUpdate(85, "Finishing"));
    }

    @Test
    void apply_cachedNodes_areLeftOutOfTheTotal() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        comfyProgressTracker.apply(new ExecutionCached(PROMPT_ID, List.of("4", "6", "8", "9")));

        assertThat(comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "4"))).isEmpty();
        assertThat(comfyProgressTracker.apply(new NodeProgress(PROMPT_ID, "3", 15, 30)))
                .contains(new ProgressUpdate(50, "KSampler: step 15/30"));
        assertThat(comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "3")))
                .map(ProgressUpdate::percent)
                .contains(85);
    }

    @Test
    void apply_secondSamplerRestartingSteps_neverMovesBackwards() {
        ComfyProgressTracker comfyProgressTracker = tracker(TWO_SAMPLER_WORKFLOW);

        comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "1"));
        int firstPassDone = comfyProgressTracker.apply(new NodeProgress(PROMPT_ID, "1", 20, 20)).orElseThrow().percent();
        int afterExecuted = comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "1")).orElseThrow().percent();

        comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "2"));
        ProgressUpdate secondPass = comfyProgressTracker.apply(new NodeProgress(PROMPT_ID, "2", 1, 20)).orElseThrow();

        assertThat(firstPassDone).isEqualTo(50);
        assertThat(afterExecuted).isEqualTo(50);
        assertThat(secondPass.percent()).isGreaterThanOrEqualTo(afterExecuted);
        assertThat(secondPass.message()).isEqualTo("Second pass: step 1/20");
    }

    @Test
    void apply_otherPromptsEvents_areIgnored() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        assertThat(comfyProgressTracker.apply(new NodeExecuting("other-prompt", "4"))).isEmpty();
        assertThat(comfyProgressTracker.apply(new NodeProgress("other-prompt", "3", 10, 30))).isEmpty();
        assertThat(comfyProgressTracker.apply(new NodeExecuted("other-prompt", "4"))).isEmpty();
    }

    /**
     * Subgraph expansion runs node ids that are not in the submitted graph: they are named by id and
     * never counted as finished.
     */
    @Test
    void apply_nodeNotInTheWorkflow_isNamedByIdAndNotCounted() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        assertThat(comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, "12.0.1")))
                .map(ProgressUpdate::message)
                .contains("Running node 12.0.1");
        assertThat(comfyProgressTracker.apply(new NodeExecuted(PROMPT_ID, "12.0.1"))).isEmpty();
    }

    @Test
    void apply_endOfPromptSignal_isLeftToTheCaller() {
        ComfyProgressTracker comfyProgressTracker = tracker(FIVE_NODE_WORKFLOW);

        assertThat(comfyProgressTracker.apply(new NodeExecuting(PROMPT_ID, null))).isEmpty();
    }

    private ComfyProgressTracker tracker(String workflowJson) {
        return new ComfyProgressTracker((ObjectNode) jsonMapper.readTree(workflowJson), PROMPT_ID);
    }
}
