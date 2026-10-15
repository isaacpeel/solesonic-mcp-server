package com.solesonic.agent.image.node;

import com.solesonic.agent.image.ImageIntent;
import com.solesonic.agent.image.ImageIntentClassification;
import com.solesonic.agent.image.ImageRequestMessage;
import com.solesonic.agent.image.ImageWorkflowSelection;
import com.solesonic.agent.image.ImageWorkflowState;
import com.solesonic.agent.image.WorkflowCandidate;
import com.solesonic.agent.image.WorkflowChoice;
import com.solesonic.mcp.exception.ToolFailures;
import com.solesonic.model.comfyui.RegisteredComfyWorkflow;
import com.solesonic.service.comfyui.ComfyWorkflowRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.solesonic.agent.config.ImageChatClientConfig.IMAGE_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Chooses the workflow. Candidates are the workflows with a graph for the attached number of
 * images, narrowed to those that serve the classified intent. One candidate is chosen without a
 * model call; several are put to the routing model, which picks by each workflow's description.
 *
 * <p>This step only chooses: the prompt is written afterwards by {@link WriteImagePromptNode}, which
 * knows the chosen workflow. A failing model does not fail the call: the selection is left empty
 * and {@link ResolveImageWorkflowNode} falls back to the first candidate.
 */
@Component
public class SelectImageWorkflowNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(SelectImageWorkflowNode.class);

    private static final String OPERATION = "Selecting an image workflow";

    private static final String INTENT_GUIDANCE = "intentGuidance";
    private static final String WORKFLOWS = "workflows";

    private static final String MODEL_FAILED_RATIONALE = "The routing model failed, so no workflow was chosen by it.";
    private static final String NO_CANDIDATES_RATIONALE = "No workflow accepts the attached reference images.";
    private static final String ONLY_CANDIDATE_RATIONALE = "It is the only workflow configured for this request.";

    private static final String UNDECIDED_INTENT_GUIDANCE = """
            The request's intent could not be decided. Use the user message and each workflow's \
            description to choose the best fit.""";

    private static final Map<ImageIntent, String> INTENT_GUIDANCE_TEXT = Map.of(
            ImageIntent.NEW_IMAGE, """
                    The request is for a brand new image: no photo is attached. Choose the workflow \
                    whose description best fits the subject and style requested.""",
            ImageIntent.RESTYLE_PHOTO, """
                    The request is to restyle the attached photo: keep its composition and give it a \
                    new style, medium, lighting or mood. The attached photo WILL be used.""",
            ImageIntent.EDIT_PERSON_IN_PHOTO, """
                    The request is to keep the person in the attached photo and show them somewhere \
                    else, doing something else, or dressed differently. The attached photo WILL be used.""");

    private final ChatClient chatClient;
    private final ComfyWorkflowRegistry comfyWorkflowRegistry;

    // A constructor parameter rather than a @Value field so tests can supply their own template.
    private final Resource selectImageWorkflowPrompt;

    public SelectImageWorkflowNode(@Qualifier(IMAGE_CHAT_CLIENT) ChatClient chatClient,
                                   ComfyWorkflowRegistry comfyWorkflowRegistry,
                                   @Value("classpath:/prompt/image/select_image_workflow.st") Resource selectImageWorkflowPrompt) {
        this.chatClient = chatClient;
        this.comfyWorkflowRegistry = comfyWorkflowRegistry;
        this.selectImageWorkflowPrompt = selectImageWorkflowPrompt;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(ImageWorkflowState imageWorkflowState) {
        try {
            String userPrompt = imageWorkflowState.userPrompt()
                    .filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new IllegalStateException(
                            "The image workflow state carries no prompt to route."));

            int referenceImageCount = imageWorkflowState.referenceImageCount().orElse(0);
            ImageIntentClassification imageIntentClassification = imageWorkflowState.imageIntent()
                    .orElse(new ImageIntentClassification(null, null));
            ImageIntent imageIntent = imageIntentClassification.intent();

            List<WorkflowCandidate> candidates = candidates(referenceImageCount, imageIntent);

            log.info("Selecting image workflow for intent {} and {} reference image(s): {} candidate(s) {}",
                    imageIntent, referenceImageCount, candidates.size(),
                    candidates.stream().map(WorkflowCandidate::toolName).toList());

            WorkflowChoice workflowChoice;

            if (candidates.isEmpty()) {
                log.info("No workflow accepts {} reference image(s); skipping the routing model", referenceImageCount);
                workflowChoice = new WorkflowChoice(null, NO_CANDIDATES_RATIONALE);
            } else if (candidates.size() == 1) {
                log.info("Selected image workflow '{}' without asking the model: it is the only candidate",
                        candidates.getFirst().toolName());
                workflowChoice = new WorkflowChoice(candidates.getFirst().toolName(), ONLY_CANDIDATE_RATIONALE);
            } else {
                List<String> referenceImageDescriptions = imageWorkflowState.referenceImageDescriptions().orElse(List.of());
                workflowChoice = choose(imageIntent, candidates,
                        ImageRequestMessage.withImageDescriptions(userPrompt, referenceImageCount, referenceImageDescriptions));
            }

            ImageWorkflowSelection imageWorkflowSelection = new ImageWorkflowSelection(
                    workflowChoice.toolName(), null,
                    rationale(imageIntentClassification.rationale(), workflowChoice.rationale()));

            return completedFuture(Map.of(
                    ImageWorkflowState.CANDIDATE_WORKFLOWS, candidates,
                    ImageWorkflowState.WORKFLOW_SELECTION, imageWorkflowSelection));
        } catch (Exception exception) {
            log.error("Failed to select an image workflow using prompt resource {}", selectImageWorkflowPrompt, exception);
            return failedFuture(ToolFailures.describe(OPERATION, exception));
        }
    }

    /**
     * Workflows with a graph for exactly the attached number of images, narrowed to those serving the
     * intent. When the intent is undecided or no such workflow serves it, every workflow that can take
     * the images is offered rather than none.
     */
    private List<WorkflowCandidate> candidates(int referenceImageCount, ImageIntent imageIntent) {
        List<RegisteredComfyWorkflow> acceptingImages = comfyWorkflowRegistry.registeredWorkflows().stream()
                .filter(registeredComfyWorkflow -> referenceImageCount == 0
                        || registeredComfyWorkflow.acceptsReferenceImages(referenceImageCount))
                .toList();

        List<RegisteredComfyWorkflow> forIntent = acceptingImages.stream()
                .filter(registeredComfyWorkflow -> registeredComfyWorkflow.servesIntent(imageIntent))
                .toList();

        if (forIntent.isEmpty()) {
            log.info("No workflow that accepts {} reference image(s) serves intent {}; offering all that accept them",
                    referenceImageCount, imageIntent);

            return acceptingImages.stream().map(SelectImageWorkflowNode::candidate).toList();
        }

        return forIntent.stream().map(SelectImageWorkflowNode::candidate).toList();
    }

    private static WorkflowCandidate candidate(RegisteredComfyWorkflow registeredComfyWorkflow) {
        return new WorkflowCandidate(
                registeredComfyWorkflow.toolName(),
                registeredComfyWorkflow.name(),
                registeredComfyWorkflow.description());
    }

    /**
     * The template is rendered outside the fallback: a template that cannot render is a programming
     * error and fails the call, whereas only the model call itself falls back.
     */
    private WorkflowChoice choose(ImageIntent imageIntent, List<WorkflowCandidate> candidates, String userMessage) {
        String instructions = PromptTemplate.builder()
                .resource(selectImageWorkflowPrompt)
                .build()
                .render(Map.of(
                        INTENT_GUIDANCE, intentGuidance(imageIntent),
                        WORKFLOWS, describe(candidates)));

        log.info("Asking the routing model to choose among {} workflow(s)", candidates.size());

        long startedAt = System.nanoTime();

        try {
            WorkflowChoice workflowChoice = chatClient.prompt()
                    .system(instructions)
                    .user(userMessage)
                    .call()
                    .responseEntity(WorkflowChoice.class)
                    .getEntity();

            if (workflowChoice == null) {
                log.warn("The image routing model returned no parseable {} after {} ms",
                        WorkflowChoice.class.getSimpleName(), elapsedMillis(startedAt));
                return new WorkflowChoice(null, MODEL_FAILED_RATIONALE);
            }

            log.info("Image router chose workflow '{}' in {} ms: {}", workflowChoice.toolName(),
                    elapsedMillis(startedAt), workflowChoice.rationale());

            return workflowChoice;
        } catch (Exception exception) {
            log.warn("The image routing model failed after {} ms; falling back to the first candidate",
                    elapsedMillis(startedAt), exception);
            return new WorkflowChoice(null, MODEL_FAILED_RATIONALE);
        }
    }

    // Map.of rejects a null key even in a lookup, so an undecided intent is handled before it.
    private static String intentGuidance(ImageIntent imageIntent) {
        if (imageIntent == null) {
            return UNDECIDED_INTENT_GUIDANCE;
        }

        return INTENT_GUIDANCE_TEXT.getOrDefault(imageIntent, UNDECIDED_INTENT_GUIDANCE);
    }

    private static String describe(List<WorkflowCandidate> candidates) {
        return candidates.stream()
                .map(workflowCandidate -> """
                        - toolName: %s
                          name: %s
                          description: %s"""
                        .formatted(workflowCandidate.toolName(), workflowCandidate.name(),
                                Objects.toString(workflowCandidate.description(), "").strip()))
                .collect(Collectors.joining("\n"));
    }

    private static String rationale(String intentRationale, String choiceRationale) {
        List<String> parts = new ArrayList<>();

        if (StringUtils.isNotBlank(intentRationale)) {
            parts.add(intentRationale.strip());
        }

        if (StringUtils.isNotBlank(choiceRationale)) {
            parts.add(choiceRationale.strip());
        }

        return String.join(" ", parts);
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
