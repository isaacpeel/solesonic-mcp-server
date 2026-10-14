package com.solesonic.agent.image;

import com.solesonic.agent.state.IdentifiedAgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State for the image workflow router. Carries the prompt, the <em>number</em> of attached reference
 * images and the caller's short description of each, never the images themselves: state is
 * checkpointed to Redis, and one request's images can run to tens of megabytes of base64.
 */
public class ImageWorkflowState extends IdentifiedAgentState {

    public static final String USER_PROMPT = "userPrompt";
    public static final String REFERENCE_IMAGE_COUNT = "referenceImageCount";
    public static final String REFERENCE_IMAGE_DESCRIPTIONS = "referenceImageDescriptions";
    public static final String IMAGE_INTENT = "imageIntent";
    public static final String CANDIDATE_WORKFLOWS = "candidateWorkflows";
    public static final String WORKFLOW_SELECTION = "workflowSelection";

    public ImageWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userPrompt() {
        return value(USER_PROMPT);
    }

    public Optional<Integer> referenceImageCount() {
        return value(REFERENCE_IMAGE_COUNT);
    }

    /**
     * The caller's description of each attached image, in image order; blank where none was given.
     * Read only by the routing steps, never by prompt writing.
     */
    public Optional<List<String>> referenceImageDescriptions() {
        return value(REFERENCE_IMAGE_DESCRIPTIONS);
    }

    public Optional<ImageIntentClassification> imageIntent() {
        return value(IMAGE_INTENT);
    }

    public Optional<List<WorkflowCandidate>> candidateWorkflows() {
        return value(CANDIDATE_WORKFLOWS);
    }

    public Optional<ImageWorkflowSelection> workflowSelection() {
        return value(WORKFLOW_SELECTION);
    }
}
