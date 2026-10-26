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
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Chooses the workflow. Candidates are the workflows with a graph for the attached number of
 * images, narrowed to those that serve the classified intent. One candidate is chosen without a
 * model call; several are put to the decision model as the options of one choice, labelled by tool
 * name and described by each workflow's name and description.
 *
 * <p>This step only chooses: the prompt is written afterwards by {@link WriteImagePromptNode}, which
 * knows the chosen workflow. A failing model or a choice below the confidence threshold does not
 * fail the call: the selection is left empty and {@link ResolveImageWorkflowNode} falls back to the
 * first candidate.
 */
@Component
public class SelectImageWorkflowNode implements AsyncNodeAction<ImageWorkflowState> {

    private static final Logger log = LoggerFactory.getLogger(SelectImageWorkflowNode.class);

    static final String WORKFLOW_QUESTION = "workflow";

    private static final String OPERATION = "Selecting an image workflow";

    private static final String MODEL_FAILED_RATIONALE = "The decision model failed, so no workflow was chosen by it.";
    private static final String NO_CANDIDATES_RATIONALE = "No workflow accepts the attached reference images.";
    private static final String ONLY_CANDIDATE_RATIONALE = "It is the only workflow configured for this request.";

    private static final String UNDECIDED_INTENT_GUIDANCE = """
            The request's intent could not be decided. Use the request and each workflow's \
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

    private final TypeSafeClient typeSafeClient;
    private final ComfyWorkflowRegistry comfyWorkflowRegistry;
    private final double confidenceThreshold;

    public SelectImageWorkflowNode(TypeSafeClient typeSafeClient,
                                   ComfyWorkflowRegistry comfyWorkflowRegistry,
                                   @Value("${solesonic.image.decision-confidence-threshold}") double confidenceThreshold) {
        this.typeSafeClient = typeSafeClient;
        this.comfyWorkflowRegistry = comfyWorkflowRegistry;
        this.confidenceThreshold = confidenceThreshold;
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
                log.info("No workflow accepts {} reference image(s); skipping the decision model", referenceImageCount);
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
            log.error("Failed to select an image workflow", exception);
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
     * The options are the candidates' tool names, so the answer is always one of them. Building the
     * request stays outside the fallback: a request that cannot be built is a programming error and
     * fails the call, whereas only the model call itself falls back.
     */
    private WorkflowChoice choose(ImageIntent imageIntent, List<WorkflowCandidate> candidates, String requestMessage) {
        Choice.Builder workflowChoiceQuestion = Choice.builder().instructions(intentGuidance(imageIntent));
        candidates.forEach(workflowCandidate -> workflowChoiceQuestion.option(
                workflowCandidate.toolName(), describe(workflowCandidate)));

        SystemOneRequest systemOneRequest = SystemOneRequest.builder()
                .state(requestMessage)
                .question(WORKFLOW_QUESTION, workflowChoiceQuestion.build())
                .build();

        log.info("Asking the decision model to choose among {} workflow(s)", candidates.size());

        long startedAt = System.nanoTime();

        try {
            ChoiceAnswer choiceAnswer = typeSafeClient.systemOne(systemOneRequest).choice(WORKFLOW_QUESTION);
            String confidence = String.format(Locale.ROOT, "%.2f", choiceAnswer.confidence());

            if (choiceAnswer.confidence() < confidenceThreshold) {
                log.info("Image workflow '{}' rejected in {} ms: confidence {} is below {}; probabilities {}",
                        choiceAnswer.value(), elapsedMillis(startedAt), choiceAnswer.confidence(), confidenceThreshold,
                        choiceAnswer.probabilities());
                return new WorkflowChoice(null,
                        "The decision model chose '%s' with only %s confidence, so no workflow was chosen."
                                .formatted(choiceAnswer.value(), confidence));
            }

            log.info("Decision model chose workflow '{}' in {} ms with confidence {}", choiceAnswer.value(),
                    elapsedMillis(startedAt), choiceAnswer.confidence());

            return new WorkflowChoice(choiceAnswer.value(),
                    "The decision model chose '%s' with %s confidence.".formatted(choiceAnswer.value(), confidence));
        } catch (TypeSafeException typeSafeException) {
            log.warn("The decision model failed after {} ms; falling back to the first candidate",
                    elapsedMillis(startedAt), typeSafeException);
            return new WorkflowChoice(null, MODEL_FAILED_RATIONALE);
        } catch (Exception exception) {
            log.warn("Unexpected failure choosing an image workflow after {} ms; falling back to the first candidate",
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

    private static String describe(WorkflowCandidate workflowCandidate) {
        return workflowCandidate.name() + ": " + Objects.toString(workflowCandidate.description(), "").strip();
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
