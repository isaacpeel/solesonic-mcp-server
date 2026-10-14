package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImagePromptRewrite;
import com.solesonic.agent.image.ImageRequestMessage;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.agent.config.ImageChatClientConfig.IMAGE_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Writes the final prompt for the resolved workflow. This is the only place the prompt is refined,
 * and it is done knowing the workflow and the intent, so a person edit gets an edit instruction and
 * a new image gets a full description.
 *
 * <p>The caller's image descriptions are deliberately not given to this step: they exist to choose
 * the workflow, and copying them into the prompt is what degrades an identity-preserving edit.
 *
 * <p>A failing model does not fail the call: the caller's original prompt is used as written.
 */
@Component
public class WriteImagePromptNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(WriteImagePromptNode.class);

    private static final String OPERATION = "Writing the image prompt";

    private static final String WORKFLOW_NAME = "workflowName";
    private static final String WORKFLOW_DESCRIPTION = "workflowDescription";
    private static final String PROMPT_STYLE = "promptStyle";

    private static final String ORIGINAL_PROMPT_USED = "The original prompt was used as written.";

    private static final String UNDECIDED_WITH_PHOTO_STYLE = """
            A photo is attached and the workflow WILL use it. Write the prompt as a change to the \
            attached photo: what to change and what to keep. Do not describe a brand new scene.""";

    private static final Map<ImageIntent, String> PROMPT_STYLES = Map.of(
            ImageIntent.NEW_IMAGE, """
                    No photo is attached. Write a complete description of a brand new image: subject, \
                    composition, lighting, style and color palette, in concrete visual detail.""",
            ImageIntent.RESTYLE_PHOTO, """
                    The attached photo is redrawn with its composition kept. Write the prompt as the new \
                    look: style, medium, lighting, mood and color. Refer to the photo's subject only in \
                    the user's own words, and do not invent details of the photo.""",
            ImageIntent.EDIT_PERSON_IN_PHOTO, """
                    The person in the attached photo is kept. Write a short edit instruction: what to \
                    change (setting, pose, activity, clothing, lighting) and what to keep (the same \
                    person's face, hair and identity). Refer to them as "this same person", "this same \
                    man" or "this same woman". Never describe the person's appearance.""");

    private final ChatClient chatClient;

    // A constructor parameter rather than a @Value field so tests can supply their own template.
    private final Resource writeImagePromptPrompt;

    public WriteImagePromptNode(@Qualifier(IMAGE_CHAT_CLIENT) ChatClient chatClient,
                                @Value("classpath:/prompt/image/write_image_prompt.st") Resource writeImagePromptPrompt) {
        this.chatClient = chatClient;
        this.writeImagePromptPrompt = writeImagePromptPrompt;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(ImageWorkflowState imageWorkflowState) {
        try {
            String userPrompt = imageWorkflowState.userPrompt()
                    .filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new IllegalStateException(
                            "The image workflow state carries no prompt to write from."));

            ImageWorkflowSelection selection = imageWorkflowState.workflowSelection()
                    .filter(imageWorkflowSelection -> imageWorkflowSelection.toolName() != null)
                    .orElseThrow(() -> new IllegalStateException(
                            "The image workflow state carries no resolved workflow to write a prompt for."));

            int referenceImageCount = imageWorkflowState.referenceImageCount().orElse(0);
            ImageIntent imageIntent = imageWorkflowState.imageIntent()
                    .map(ImageIntentClassification::intent)
                    .orElse(null);

            WorkflowCandidate workflowCandidate = imageWorkflowState.candidateWorkflows().orElse(List.of()).stream()
                    .filter(candidate -> candidate.toolName().equals(selection.toolName()))
                    .findFirst()
                    .orElse(new WorkflowCandidate(selection.toolName(), selection.toolName(), ""));

            log.info("Writing the image prompt for workflow '{}', intent {}, {} reference image(s)",
                    workflowCandidate.toolName(), imageIntent, referenceImageCount);

            String enhancedPrompt = write(workflowCandidate, promptStyle(imageIntent, referenceImageCount),
                    ImageRequestMessage.withoutImageDescriptions(userPrompt, referenceImageCount));

            String rationale = selection.rationale();

            if (StringUtils.isBlank(enhancedPrompt)) {
                enhancedPrompt = userPrompt;
                rationale = StringUtils.isBlank(rationale) ? ORIGINAL_PROMPT_USED : rationale + " " + ORIGINAL_PROMPT_USED;
                log.info("Using the original prompt as written for workflow '{}'", workflowCandidate.toolName());
            }

            return completedFuture(Map.of(ImageWorkflowState.WORKFLOW_SELECTION,
                    new ImageWorkflowSelection(selection.toolName(), enhancedPrompt, rationale)));
        } catch (Exception exception) {
            log.error("Failed to write the image prompt using prompt resource {}", writeImagePromptPrompt, exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }

    private static String promptStyle(ImageIntent imageIntent, int referenceImageCount) {
        if (imageIntent != null) {
            return PROMPT_STYLES.get(imageIntent);
        }

        return referenceImageCount == 0 ? PROMPT_STYLES.get(ImageIntent.NEW_IMAGE) : UNDECIDED_WITH_PHOTO_STYLE;
    }

    /**
     * @return the rewritten prompt, or null when the model failed or returned nothing usable
     */
    private String write(WorkflowCandidate workflowCandidate, String promptStyle, String userMessage) {
        String instructions = PromptTemplate.builder()
                .resource(writeImagePromptPrompt)
                .build()
                .render(Map.of(
                        WORKFLOW_NAME, Objects.toString(workflowCandidate.name(), workflowCandidate.toolName()),
                        WORKFLOW_DESCRIPTION, Objects.toString(workflowCandidate.description(), "").strip(),
                        PROMPT_STYLE, promptStyle));

        long startedAt = System.nanoTime();

        try {
            ImagePromptRewrite imagePromptRewrite = chatClient.prompt()
                    .system(instructions)
                    .user(userMessage)
                    .call()
                    .responseEntity(ImagePromptRewrite.class)
                    .getEntity();

            if (imagePromptRewrite == null || StringUtils.isBlank(imagePromptRewrite.enhancedPrompt())) {
                log.warn("The image prompt model returned no usable prompt after {} ms", elapsedMillis(startedAt));
                return null;
            }

            log.info("Image prompt written in {} ms: {}", elapsedMillis(startedAt), imagePromptRewrite.enhancedPrompt());

            return imagePromptRewrite.enhancedPrompt();
        } catch (Exception exception) {
            log.warn("The image prompt model failed after {} ms; using the original prompt", elapsedMillis(startedAt), exception);
            return null;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
