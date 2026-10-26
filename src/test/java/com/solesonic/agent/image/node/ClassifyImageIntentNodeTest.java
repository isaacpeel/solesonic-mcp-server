package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageWorkflowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassifyImageIntentNodeTest {

    private static final String USER_PROMPT = "put me on a sunny beach holding a cocktail";
    private static final String DESCRIPTION = "a man with glasses and a beard, head and shoulders";
    private static final double THRESHOLD = 0.5;

    private static final Resource TEMPLATE = new ByteArrayResource(
            "Classify the request.".getBytes(StandardCharsets.UTF_8));

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void apply_withoutReferenceImages_isANewImageWithoutAskingTheModel() throws Exception {
        ImageIntentClassification classification = intent(node().apply(state(0, List.of())).get());

        assertThat(classification.intent()).isEqualTo(ImageIntent.NEW_IMAGE);
        verifyNoInteractions(typeSafeClient);
    }

    /**
     * Only the two intents that use a photo are offered, so a new image cannot be chosen while a
     * photo is attached.
     */
    @Test
    void apply_withAReferenceImage_offersOnlyThePhotoIntentsWithTheTemplateAsInstructions() throws Exception {
        stubDecisionModelReturning(ImageIntent.EDIT_PERSON_IN_PHOTO.name(), 0.9);

        ImageIntentClassification classification = intent(node().apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isEqualTo(ImageIntent.EDIT_PERSON_IN_PHOTO);
        assertThat(classification.rationale()).contains("EDIT_PERSON_IN_PHOTO", "0.90");

        Choice intentChoice = (Choice) capturedRequest().questions().get(ClassifyImageIntentNode.INTENT_QUESTION);
        assertThat(intentChoice.criteria().keySet())
                .containsExactly(ImageIntent.RESTYLE_PHOTO.name(), ImageIntent.EDIT_PERSON_IN_PHOTO.name());
        assertThat(intentChoice.instructions()).isNotNull();
        assertThat(intentChoice.instructions().asText()).isEqualTo("Classify the request.");
    }

    /**
     * The photo facts lead the state, where the model reliably reads them, and carry the caller's
     * description of the photo.
     */
    @Test
    void apply_withAReferenceImage_putsThePhotoAndItsDescriptionInTheState() throws Exception {
        stubDecisionModelReturning(ImageIntent.EDIT_PERSON_IN_PHOTO.name(), 0.9);

        node().apply(state(1, List.of(DESCRIPTION))).get();

        assertThat(capturedRequest().state().asText())
                .startsWith("[1 photo attached]")
                .contains("Photo 1: " + DESCRIPTION)
                .endsWith("Request: " + USER_PROMPT);
    }

    @Test
    void apply_withoutADescription_saysSoInTheState() throws Exception {
        stubDecisionModelReturning(ImageIntent.RESTYLE_PHOTO.name(), 0.9);

        node().apply(state(1, List.of(""))).get();

        assertThat(capturedRequest().state().asText()).contains("Photo 1: no description given");
    }

    @Test
    void apply_confidenceExactlyAtTheThreshold_acceptsTheIntent() throws Exception {
        stubDecisionModelReturning(ImageIntent.RESTYLE_PHOTO.name(), THRESHOLD);

        ImageIntentClassification classification = intent(node().apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isEqualTo(ImageIntent.RESTYLE_PHOTO);
    }

    @Test
    void apply_confidenceBelowTheThreshold_leavesTheIntentUndecided() throws Exception {
        stubDecisionModelReturning(ImageIntent.RESTYLE_PHOTO.name(), 0.49);

        ImageIntentClassification classification = intent(node().apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isNull();
        assertThat(classification.rationale()).contains("RESTYLE_PHOTO", "0.49");
    }

    @Test
    void apply_answerIsNotAnOfferedIntent_leavesTheIntentUndecided() throws Exception {
        stubDecisionModelReturning(ImageIntent.NEW_IMAGE.name(), 0.9);

        ImageIntentClassification classification = intent(node().apply(state(1, List.of(DESCRIPTION))).get());

        assertThat(classification.intent()).isNull();
    }

    @Test
    void apply_answerMissing_leavesTheIntentUndecidedInsteadOfFailing() throws Exception {
        when(typeSafeClient.systemOne(any(SystemOneRequest.class)))
                .thenReturn(new SystemOneResponse("test-decision-model", Map.of(), null));

        CompletableFuture<Map<String, Object>> future = node().apply(state(1, List.of(DESCRIPTION)));

        assertThat(future).isCompleted();
        assertThat(intent(future.get()).intent()).isNull();
    }

    @Test
    void apply_modelThrows_leavesTheIntentUndecidedInsteadOfFailing() throws Exception {
        when(typeSafeClient.systemOne(any(SystemOneRequest.class))).thenThrow(new TypeSafeException("model offline"));

        CompletableFuture<Map<String, Object>> future = node().apply(state(1, List.of(DESCRIPTION)));

        assertThat(future).isCompleted();
        assertThat(intent(future.get()).intent()).isNull();
        assertThat(intent(future.get()).rationale()).isEqualTo("The intent could not be decided.");
    }

    @Test
    void apply_stateWithoutAPrompt_failsWithADescriptiveMessage() {
        CompletableFuture<Map<String, Object>> future = node().apply(
                new ImageWorkflowState(Map.of(ImageWorkflowState.REFERENCE_IMAGE_COUNT, 1)));

        assertThat(future).isCompletedExceptionally();
        assertThat(catchThrowable(future::join))
                .rootCause()
                .hasMessageContaining("Classifying the image request failed")
                .hasMessageContaining("carries no prompt");
    }

    private ClassifyImageIntentNode node() {
        return new ClassifyImageIntentNode(typeSafeClient, TEMPLATE, THRESHOLD);
    }

    private void stubDecisionModelReturning(String label, double confidence) {
        Map<String, Answer> answers = Map.of(ClassifyImageIntentNode.INTENT_QUESTION,
                new ChoiceAnswer(label, Map.of(label, confidence), confidence));

        when(typeSafeClient.systemOne(any(SystemOneRequest.class)))
                .thenReturn(new SystemOneResponse("test-decision-model", answers, null));
    }

    private SystemOneRequest capturedRequest() {
        ArgumentCaptor<SystemOneRequest> requestCaptor = ArgumentCaptor.forClass(SystemOneRequest.class);
        verify(typeSafeClient).systemOne(requestCaptor.capture());
        return requestCaptor.getValue();
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
