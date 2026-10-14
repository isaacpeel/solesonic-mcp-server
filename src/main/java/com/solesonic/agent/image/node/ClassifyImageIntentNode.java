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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.ImageChatClientConfig.IMAGE_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Decides what the user wants done before any workflow is considered. Without a photo the answer
 * is always {@link ImageIntent#NEW_IMAGE} and no model is asked. With a photo, the routing model
 * chooses between restyling it and editing the person in it, from the request and the caller's
 * description of each photo.
 *
 * <p>A failing or unusable answer does not fail the call: the intent is left undecided and workflow
 * selection offers every workflow that accepts the photos.
 */
@Component
public class ClassifyImageIntentNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(ClassifyImageIntentNode.class);

    private static final String OPERATION = "Classifying the image request";

    private static final String NO_PHOTO_RATIONALE = "No photo is attached, so a new image is generated.";
    private static final String MODEL_FAILED_RATIONALE = "The intent could not be decided.";
    private static final String NEW_IMAGE_WITH_PHOTO_RATIONALE =
            "The intent model chose a new image although a photo is attached, so the intent was left undecided.";

    private final ChatClient chatClient;

    // A constructor parameter rather than a @Value field so tests can supply their own template.
    private final Resource classifyImageIntentPrompt;

    public ClassifyImageIntentNode(@Qualifier(IMAGE_CHAT_CLIENT) ChatClient chatClient,
                                   @Value("classpath:/prompt/image/classify_image_intent.st") Resource classifyImageIntentPrompt) {
        this.chatClient = chatClient;
        this.classifyImageIntentPrompt = classifyImageIntentPrompt;
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
    private ImageIntentClassification classify(String userMessage) {
        String instructions = PromptTemplate.builder()
                .resource(classifyImageIntentPrompt)
                .build()
                .render();

        long startedAt = System.nanoTime();

        try {
            ImageIntentClassification imageIntentClassification = chatClient.prompt()
                    .system(instructions)
                    .user(userMessage)
                    .call()
                    .responseEntity(ImageIntentClassification.class)
                    .getEntity();

            if (imageIntentClassification == null || imageIntentClassification.intent() == null) {
                log.warn("The image intent model returned no usable intent after {} ms; offering every workflow",
                        elapsedMillis(startedAt));
                return new ImageIntentClassification(null, MODEL_FAILED_RATIONALE);
            }

            if (imageIntentClassification.intent() == ImageIntent.NEW_IMAGE) {
                log.warn("The image intent model chose {} although a photo is attached; offering every workflow",
                        ImageIntent.NEW_IMAGE);
                return new ImageIntentClassification(null, NEW_IMAGE_WITH_PHOTO_RATIONALE);
            }

            log.info("Image intent classified as {} in {} ms: {}", imageIntentClassification.intent(),
                    elapsedMillis(startedAt), imageIntentClassification.rationale());

            return imageIntentClassification;
        } catch (Exception exception) {
            log.warn("The image intent model failed after {} ms; offering every workflow", elapsedMillis(startedAt), exception);
            return new ImageIntentClassification(null, MODEL_FAILED_RATIONALE);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
