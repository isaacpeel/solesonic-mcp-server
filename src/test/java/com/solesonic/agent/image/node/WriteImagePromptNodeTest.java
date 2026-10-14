package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImagePromptRewrite;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WriteImagePromptNodeTest {

    private static final String USER_PROMPT = "put me on a sunny beach holding a cocktail";
    private static final String DESCRIPTION = "a man with glasses and a beard, head and shoulders";

    private static final Resource TEMPLATE = new ByteArrayResource("""
            Workflow: {workflowName}
            {workflowDescription}
            Style: {promptStyle}""".getBytes(StandardCharsets.UTF_8));

    private static final WorkflowCandidate PERSON_REFERENCE =
            new WorkflowCandidate("person_reference_image", "Person Reference Image", "Edits a person.");

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Test
    void apply_personEdit_writesAnEditInstructionForTheChosenWorkflow() throws Exception {
        stubChatClientReturning(new ImagePromptRewrite("Place this same man on a sunny beach holding a cocktail."));
        WriteImagePromptNode node = new WriteImagePromptNode(chatClient, TEMPLATE);

        ImageWorkflowSelection selection = selection(node.apply(state(ImageIntent.EDIT_PERSON_IN_PHOTO)).get());

        assertThat(selection.toolName()).isEqualTo("person_reference_image");
        assertThat(selection.enhancedPrompt()).isEqualTo("Place this same man on a sunny beach holding a cocktail.");
        assertThat(selection.rationale()).isEqualTo("Edits a person.");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).system(systemCaptor.capture());
        assertThat(systemCaptor.getValue())
                .contains("Person Reference Image", "Edits a person.", "edit instruction", "Never describe the person's appearance");
    }

    /**
     * Descriptions choose the workflow; copying them into the prompt is what degrades an
     * identity-preserving edit, so this step never sees them.
     */
    @Test
    void apply_neverGivesTheImageDescriptionsToThePromptWriter() throws Exception {
        stubChatClientReturning(new ImagePromptRewrite("Place this same man on a beach."));
        WriteImagePromptNode node = new WriteImagePromptNode(chatClient, TEMPLATE);

        node.apply(state(ImageIntent.EDIT_PERSON_IN_PHOTO)).get();

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).system(systemCaptor.capture());
        verify(chatClientRequestSpec).user(userCaptor.capture());

        assertThat(systemCaptor.getValue()).doesNotContain(DESCRIPTION);
        assertThat(userCaptor.getValue())
                .doesNotContain(DESCRIPTION)
                .isEqualTo("[1 photo attached]\nRequest: " + USER_PROMPT);
    }

    @Test
    void apply_modelThrows_usesTheOriginalPromptAndSaysSo() throws Exception {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenThrow(new IllegalStateException("model offline"));
        WriteImagePromptNode node = new WriteImagePromptNode(chatClient, TEMPLATE);

        CompletableFuture<Map<String, Object>> future = node.apply(state(ImageIntent.EDIT_PERSON_IN_PHOTO));

        assertThat(future).isCompleted();
        ImageWorkflowSelection selection = selection(future.get());
        assertThat(selection.enhancedPrompt()).isEqualTo(USER_PROMPT);
        assertThat(selection.rationale()).contains("original prompt was used");
    }

    @Test
    void apply_modelReturnsABlankPrompt_usesTheOriginalPrompt() throws Exception {
        stubChatClientReturning(new ImagePromptRewrite("  "));
        WriteImagePromptNode node = new WriteImagePromptNode(chatClient, TEMPLATE);

        ImageWorkflowSelection selection = selection(node.apply(state(ImageIntent.RESTYLE_PHOTO)).get());

        assertThat(selection.enhancedPrompt()).isEqualTo(USER_PROMPT);
    }

    @Test
    void apply_stateWithoutAResolvedWorkflow_failsWithADescriptiveMessage() {
        WriteImagePromptNode node = new WriteImagePromptNode(chatClient, TEMPLATE);

        CompletableFuture<Map<String, Object>> future = node.apply(new ImageWorkflowState(Map.of(
                ImageWorkflowState.USER_PROMPT, USER_PROMPT)));

        assertThat(future).isCompletedExceptionally();
        assertThat(catchThrowable(future::join))
                .rootCause()
                .hasMessageContaining("Writing the image prompt failed")
                .hasMessageContaining("no resolved workflow");
    }

    private void stubChatClientReturning(ImagePromptRewrite imagePromptRewrite) {
        ResponseEntity<ChatResponse, ImagePromptRewrite> responseEntity = new ResponseEntity<>(null, imagePromptRewrite);

        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(ImagePromptRewrite.class)).thenReturn(responseEntity);
    }

    private static ImageWorkflowState state(ImageIntent imageIntent) {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(ImageWorkflowState.USER_PROMPT, USER_PROMPT);
        stateData.put(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 1);
        stateData.put(ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, List.of(DESCRIPTION));
        stateData.put(ImageWorkflowState.IMAGE_INTENT, new ImageIntentClassification(imageIntent, "Intent."));
        stateData.put(ImageWorkflowState.CANDIDATE_WORKFLOWS, List.of(PERSON_REFERENCE));
        stateData.put(ImageWorkflowState.WORKFLOW_SELECTION,
                new ImageWorkflowSelection("person_reference_image", null, "Edits a person."));

        return new ImageWorkflowState(stateData);
    }

    private static ImageWorkflowSelection selection(Map<String, Object> result) {
        return new ImageWorkflowState(result).workflowSelection().orElseThrow();
    }
}
