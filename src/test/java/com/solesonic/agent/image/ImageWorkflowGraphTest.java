package com.solesonic.agent.image;

import com.solesonic.agent.image.node.ClassifyImageIntentNode;
import com.solesonic.agent.image.node.ResolveImageWorkflowNode;
import com.solesonic.agent.image.node.SelectImageWorkflowNode;
import com.solesonic.agent.image.node.WriteImagePromptNode;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageWorkflowGraphTest {

    @Mock
    private ClassifyImageIntentNode classifyImageIntentNode;

    @Mock
    private SelectImageWorkflowNode selectImageWorkflowNode;

    @Mock
    private WriteImagePromptNode writeImagePromptNode;

    /**
     * Runs intent, selection, resolution and prompt writing in order: the resolve node repairs the
     * hallucinated pick, and the prompt is then written for the repaired workflow.
     */
    @Test
    void graph_classifiesSelectsResolvesThenWritesThePromptForTheResolvedWorkflow() throws Exception {
        when(classifyImageIntentNode.apply(any(ImageWorkflowState.class))).thenReturn(completedFuture(Map.of(
                ImageWorkflowState.IMAGE_INTENT, new ImageIntentClassification(ImageIntent.NEW_IMAGE, "No photo."))));

        when(selectImageWorkflowNode.apply(any(ImageWorkflowState.class))).thenReturn(completedFuture(Map.of(
                ImageWorkflowState.CANDIDATE_WORKFLOWS, List.of(new WorkflowCandidate("my_workflow", "My workflow", "General.")),
                ImageWorkflowState.WORKFLOW_SELECTION, new ImageWorkflowSelection("made_up", null, "Guess."))));

        when(writeImagePromptNode.apply(any(ImageWorkflowState.class))).thenAnswer(invocation -> {
            ImageWorkflowState imageWorkflowState = invocation.getArgument(0);
            ImageWorkflowSelection resolved = imageWorkflowState.workflowSelection().orElseThrow();

            assertThat(imageWorkflowState.imageIntent().orElseThrow().intent()).isEqualTo(ImageIntent.NEW_IMAGE);

            return completedFuture(Map.of(ImageWorkflowState.WORKFLOW_SELECTION,
                    new ImageWorkflowSelection(resolved.toolName(), "an enhanced prompt for " + resolved.toolName(),
                            resolved.rationale())));
        });

        CompiledGraph<ImageWorkflowState> graph = new ImageWorkflowGraph().imageRouterGraph(
                classifyImageIntentNode,
                selectImageWorkflowNode,
                new ResolveImageWorkflowNode(),
                writeImagePromptNode,
                CompileConfig.builder().build());

        ImageWorkflowState finalState = graph.invoke(
                        GraphInput.args(Map.of(
                                ImageWorkflowState.USER_PROMPT, "a lighthouse",
                                ImageWorkflowState.REFERENCE_IMAGE_COUNT, 0)),
                        RunnableConfig.builder().build())
                .orElseThrow();

        ImageWorkflowSelection selection = finalState.workflowSelection().orElseThrow();

        assertThat(selection.toolName()).isEqualTo("my_workflow");
        assertThat(selection.enhancedPrompt()).isEqualTo("an enhanced prompt for my_workflow");
        assertThat(selection.rationale()).contains("made_up");
    }
}
