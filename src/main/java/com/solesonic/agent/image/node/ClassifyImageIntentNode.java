package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageRequestMessage;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.mcp.exception.ToolFailures;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Decides what the user wants done before any workflow is considered. Without a photo the answer
 * is always {@link ImageIntent#NEW_IMAGE} and no model is asked. With a photo, the decision model
 * chooses between restyling it and editing the person in it, from the request and the caller's
 * description of each photo.
 *
 * <p>A failing, unusable or low-confidence answer does not fail the call: the intent is left
 * undecided and workflow selection offers every workflow that accepts the photos.
 */
@Component
public class ClassifyImageIntentNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(ClassifyImageIntentNode.class);

    static final String INTENT_QUESTION = "intent";

    private static final String OPERATION = "Classifying the image request";

    private static final String NO_PHOTO_RATIONALE = "No photo is attached, so a new image is generated.";
    private static final String MODEL_FAILED_RATIONALE = "The intent could not be decided.";

    // Only the intents that use the photo are offered: with a photo attached, a new image is never right.
    private static final Map<ImageIntent, String> PHOTO_INTENTS = Map.of(
            ImageIntent.RESTYLE_PHOTO, """
                    Redraw the photo itself in a new style, medium, lighting or mood while keeping its \
                    composition and content.""",
            ImageIntent.EDIT_PERSON_IN_PHOTO, """
                    Show the same person from the photo somewhere else, doing something else, posed or \
                    dressed differently.""");

    private static final List<ImageIntent> PHOTO_INTENT_ORDER = List.of(
            ImageIntent.RESTYLE_PHOTO, ImageIntent.EDIT_PERSON_IN_PHOTO);

    private final TypeSafeClient typeSafeClient;

    // A constructor parameter rather than a @Value field so tests can supply their own template.
    private final Resource classifyImageIntentPrompt;

    private final double confidenceThreshold;

    public ClassifyImageIntentNode(TypeSafeClient typeSafeClient,
                                   @Value("classpath:/prompt/image/classify_image_intent.st") Resource classifyImageIntentPrompt,
                                   @Value("${solesonic.image.decision-confidence-threshold}") double confidenceThreshold) {
        this.typeSafeClient = typeSafeClient;
        this.classifyImageIntentPrompt = classifyImageIntentPrompt;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(ImageWorkflowState imageWorkflowState) {
        try {
            String userPrompt = imageWorkflowState.userPrompt()
                    .filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new IllegalStateException(
                            "The image workflow state carries no prompt to classify."));

            int referenceImageCount = imageWorkflowState.referenceImageCount().orElse(0);

            if (referenceImageCount == 0) {
                log.info("Image intent is {} without asking the model: no reference images attached", ImageIntent.NEW_IMAGE);

                return completedFuture(Map.of(ImageWorkflowState.IMAGE_INTENT,
                        new ImageIntentClassification(ImageIntent.NEW_IMAGE, NO_PHOTO_RATIONALE)));
            }

            List<String> referenceImageDescriptions = imageWorkflowState.referenceImageDescriptions().orElse(List.of());

            log.info("Classifying image intent: {} reference image(s), {} with a description, prompt of {} characters",
                    referenceImageCount,
                    referenceImageDescriptions.stream().filter(StringUtils::isNotBlank).count(),
                    userPrompt.length());

            ImageIntentClassification imageIntentClassification = classify(
                    ImageRequestMessage.withImageDescriptions(userPrompt, referenceImageCount, referenceImageDescriptions));

            return completedFuture(Map.of(ImageWorkflowState.IMAGE_INTENT, imageIntentClassification));
        } catch (Exception exception) {
            log.error("Failed to classify the image intent using prompt resource {}", classifyImageIntentPrompt, exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }

    /**
     * The template is rendered outside the fallback: a template that cannot render is a programming
     * error and fails the call, whereas only the model call itself falls back.
     */
    private ImageIntentClassification classify(String requestMessage) {
        String instructions = PromptTemplate.builder()
                .resource(classifyImageIntentPrompt)
                .build()
                .render();

        Choice.Builder intentChoice = Choice.builder().instructions(instructions);
        PHOTO_INTENT_ORDER.forEach(imageIntent -> intentChoice.option(imageIntent.name(), PHOTO_INTENTS.get(imageIntent)));

        SystemOneRequest systemOneRequest = SystemOneRequest.builder()
                .state(requestMessage)
                .question(INTENT_QUESTION, intentChoice.build())
                .build();

        long startedAt = System.nanoTime();

        try {
            ChoiceAnswer choiceAnswer = typeSafeClient.systemOne(systemOneRequest).choice(INTENT_QUESTION);
            ImageIntent imageIntent = offeredIntent(choiceAnswer.value());

            if (imageIntent == null) {
                log.warn("The decision model answered '{}', which is not an offered intent, after {} ms; offering every workflow",
                        choiceAnswer.value(), elapsedMillis(startedAt));
                return new ImageIntentClassification(null, MODEL_FAILED_RATIONALE);
            }

            if (choiceAnswer.confidence() < confidenceThreshold) {
                log.info("Image intent {} rejected in {} ms: confidence {} is below {}; probabilities {}",
                        imageIntent, elapsedMillis(startedAt), choiceAnswer.confidence(), confidenceThreshold,
                        choiceAnswer.probabilities());
                return new ImageIntentClassification(null,
                        "The decision model chose %s with only %s confidence, so the intent was left undecided."
                                .formatted(imageIntent, confidence(choiceAnswer)));
            }

            log.info("Image intent classified as {} in {} ms with confidence {}", imageIntent,
                    elapsedMillis(startedAt), choiceAnswer.confidence());

            return new ImageIntentClassification(imageIntent,
                    "The decision model chose %s with %s confidence.".formatted(imageIntent, confidence(choiceAnswer)));
        } catch (TypeSafeException typeSafeException) {
            log.warn("The decision model failed to classify the image intent after {} ms; offering every workflow",
                    elapsedMillis(startedAt), typeSafeException);
            return new ImageIntentClassification(null, MODEL_FAILED_RATIONALE);
        } catch (Exception exception) {
            log.warn("Unexpected failure classifying the image intent after {} ms; offering every workflow",
                    elapsedMillis(startedAt), exception);
            return new ImageIntentClassification(null, MODEL_FAILED_RATIONALE);
        }
    }

    private static ImageIntent offeredIntent(String label) {
        return PHOTO_INTENT_ORDER.stream()
                .filter(imageIntent -> imageIntent.name().equals(label))
                .findFirst()
                .orElse(null);
    }

    private static String confidence(ChoiceAnswer choiceAnswer) {
        return String.format(Locale.ROOT, "%.2f", choiceAnswer.confidence());
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
