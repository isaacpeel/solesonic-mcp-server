package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageWorkflowState;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifyImageIntentNodeTest {

    private static final String USER_PROMPT = "put me on a sunny beach holding a cocktail";
    private static final String DESCRIPTION = "a man with glasses and a beard, head and shoulders";

    private static final Resource TEMPLATE = new ByteArrayResource(
            "Classify the request.".getBytes(StandardCharsets.UTF_8));

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Test
    void apply_withoutReferenceImages_isANewImageWithoutAskingTheModel() throws Exception {
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        ImageIntentClassification classification = intent(node.apply(state(0, List.of())).get());

        assertThat(classification.intent()).isEqualTo(ImageIntent.NEW_IMAGE);
        verifyNoInteractions(chatClient);
    }

    /**
     * The photo facts lead the user turn, where a small model reliably reads them, and carry the
     * caller's description of the photo.
     */
    @Test
    void apply_withAReferenceImage_putsThePhotoAndItsDescriptionInTheUserMessage() throws Exception {
        stubChatClientReturning(new ImageIntentClassification(ImageIntent.EDIT_PERSON_IN_PHOTO, "Moves the person."));
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        ImageIntentClassification classification = intent(node.apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isEqualTo(ImageIntent.EDIT_PERSON_IN_PHOTO);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(userCaptor.capture());
        assertThat(userCaptor.getValue())
                .startsWith("[1 photo attached]")
                .contains("Photo 1: " + DESCRIPTION)
                .endsWith("Request: " + USER_PROMPT);
    }

    @Test
    void apply_withoutADescription_saysSoInTheUserMessage() throws Exception {
        stubChatClientReturning(new ImageIntentClassification(ImageIntent.RESTYLE_PHOTO, "Restyle."));
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        node.apply(state(1, List.of(""))).get();

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(userCaptor.capture());
        assertThat(userCaptor.getValue()).contains("Photo 1: no description given");
    }

    /**
     * With a photo attached every workflow uses it, so "new image" is not a usable answer.
     */
    @Test
    void apply_modelChoosesANewImageDespiteAPhoto_leavesTheIntentUndecided() throws Exception {
        stubChatClientReturning(new ImageIntentClassification(ImageIntent.NEW_IMAGE, "New image."));
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        ImageIntentClassification classification = intent(node.apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isNull();
    }

    @Test
    void apply_modelThrows_leavesTheIntentUndecidedInsteadOfFailing() throws Exception {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenThrow(new IllegalStateException("model offline"));
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        CompletableFuture<Map<String, Object>> future = node.apply(state(1, List.of(DESCRIPTION)));

        assertThat(future).isCompleted();
        assertThat(intent(future.get()).intent()).isNull();
    }

    @Test
    void apply_stateWithoutAPrompt_failsWithADescriptiveMessage() {
        ClassifyImageIntentNode node = new ClassifyImageIntentNode(chatClient, TEMPLATE);

        CompletableFuture<Map<String, Object>> future = node.apply(
                new ImageWorkflowState(Map.of(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 1)));

        assertThat(future).isCompletedExceptionally();
        assertThat(catchThrowable(future::join))
                .rootCause()
                .hasMessageContaining("Classifying the image request failed")
                .hasMessageContaining("carries no prompt");
    }

    private void stubChatClientReturning(ImageIntentClassification imageIntentClassification) {
        ResponseEntity<ChatResponse, ImageIntentClassification> responseEntity =
                new ResponseEntity<>(null, imageIntentClassification);

        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.responseEntity(ImageIntentClassification.class)).thenReturn(responseEntity);
    }

    private static ImageWorkflowState state(int referenceImageCount, List<String> referenceImageDescriptions) {
        return new ImageWorkflowState(Map.of(
                ImageWorkflowState.USER_PROMPT, USER_PROMPT,
                ImageWorkflowState.REFERENCE_IMAGE_COUNT, referenceImageCount,
                ImageWorkflowState.REFERENCE_IMAGE_DESCRIPTIONS, referenceImageDescriptions));
    }

    private static ImageIntentClassification intent(Map<String, Object> result) {
        return new ImageWorkflowState(result).imageIntent().orElseThrow();
    }
}
