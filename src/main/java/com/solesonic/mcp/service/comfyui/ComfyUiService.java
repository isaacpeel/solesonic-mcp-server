package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.model.comfyui.*;
import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.COMFY_UI_WEB_CLIENT;

@Service
public class ComfyUiService {

    private static final Logger log = LoggerFactory.getLogger(ComfyUiService.class);

    public static final String PROMPT = "prompt";
    public static final String TEXT = "text";
    public static final String SEED = "seed";
    public static final String VIEW = "view";
    @SuppressWarnings("unused")
    public static final String FILENAME = "filename";
    @SuppressWarnings("unused")
    public static final String SUBFOLDER = "subfolder";
    @SuppressWarnings("unused")
    public static final String TYPE = "type";
    public static final String JOBS = "jobs";
    public static final String API = "api";

    private static final String PROMPT_NODE = "58";
    private static final String SEED_NODE = "57:3";
    @SuppressWarnings("unused")
    private static final String PREVIEW_OUTPUT = "preview_output";

    private final JsonMapper jsonMapper;
    private final WebClient comfyUiWebClient;
    private final ComfyUiWebSocketClient webSocketClient;

    @Value("classpath:comfyui/base-image-workflow.json")
    private Resource workflowFile;

    /**
     * Internal record to store image output information.
     */
    @SuppressWarnings("unused")
    private record ImageOutputInfo(String filename, String subfolder, String type) {
    }

    /**
     * Result of starting an image generation job.
     * Contains job metadata and a reactive stream of status updates.
     *
     * @param jobId the unique job identifier
     * @param clientId the WebSocket client identifier
     * @param promptId the ComfyUI prompt identifier (null if submission failed)
     * @param initialStatus the initial status of the job
     * @param statusFlux a Flux emitting status updates for the job (null if submission failed)
     */
    @SuppressWarnings("unused")
    public record ImageGenerationJobResult(
            String jobId,
            UUID clientId,
            String promptId,
            ImageGenerationStatus initialStatus,
            Flux<ImageGenerationStatus> statusFlux
    ) {
    }

    public ComfyUiService(
            JsonMapper jsonMapper,
            @Qualifier(COMFY_UI_WEB_CLIENT) WebClient comfyUiWebClient,
            ComfyUiWebSocketClient webSocketClient) {
        this.comfyUiWebClient = comfyUiWebClient;
        this.webSocketClient = webSocketClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Establishes a WebSocket connection to ComfyUI for the given clientId.
     * Returns a reactive stream of events from ComfyUI.
     *
     * @param clientId the unique client identifier
     * @return a Flux of WebSocket events
     */
    public Flux<ComfyWebSocketEvent> establishWebSocket(UUID clientId) {
        log.info("Establishing WebSocket connection for clientId: {}", clientId);

        Flux<ComfyWebSocketEvent> eventFlux = webSocketClient.openSession(clientId);

        log.info("WebSocket connection established for clientId: {}", clientId);

        return eventFlux;
    }

    /**
     * Closes the WebSocket session for the given clientId.
     *
     * @param clientId the client identifier
     */
    public void closeWebSocket(UUID clientId) {
        log.info("Closing WebSocket connection for clientId: {}", clientId);
        webSocketClient.closeSession(clientId);
    }



    /**
     * Generates an image using ComfyUI with the given prompt.
     * This is a convenience method that does not associate with a WebSocket session.
     *
     * @param userPrompt the text prompt for image generation
     * @return the workflow response containing the prompt ID
     */
    public ComfyWorkflowResponse generateImage(String userPrompt) {
        return generateImage(userPrompt, null);
    }

    /**
     * Generates an image using ComfyUI with the given prompt and clientId.
     * The clientId is used to correlate WebSocket events with this prompt submission.
     *
     * @param userPrompt the text prompt for image generation
     * @param clientId the client identifier for WebSocket correlation (can be null)
     * @return the workflow response containing the prompt ID
     */
    public ComfyWorkflowResponse generateImage(String userPrompt, UUID clientId) {
        log.info("Submitting prompt to ComfyUI: {}", userPrompt);

        ComfyWorkflow comfyWorkflow = loadWorkflowTemplate();

        ComfyPrompt comfyPrompt = comfyWorkflow.getPrompt();
        Map<String, ComfyNode> promptNodes = comfyPrompt.getNodes();

        ComfyNode textPromptNode = promptNodes.get(PROMPT_NODE);
        ComfyNode seedNode = promptNodes.get(SEED_NODE);

        if (textPromptNode == null || seedNode == null) {
            throw new IllegalStateException("Missing required nodes in workflow template.");
        }

        textPromptNode.getInputs().put(TEXT, userPrompt);
        promptNodes.put(PROMPT_NODE, textPromptNode);

        long seed = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        seedNode.getInputs().put(SEED, seed);
        promptNodes.put(SEED_NODE, seedNode);

        comfyPrompt.setNodes(promptNodes);
        comfyWorkflow.setPrompt(comfyPrompt);

        if (clientId != null) {
            comfyWorkflow.setClientId(clientId.toString());
        }

        ComfyWorkflowResponse comfyWorkflowResponse = comfyUiWebClient.post()
                .uri(uriBuilder -> uriBuilder.pathSegment(PROMPT).build())
                .bodyValue(comfyWorkflow)
                .retrieve()
                .bodyToMono(ComfyWorkflowResponse.class)
                .block();

        assert comfyWorkflowResponse != null;
        log.info("Successfully submitted prompt to ComfyUI with promptId: {}", comfyWorkflowResponse.getPromptId());

        return comfyWorkflowResponse;
    }

    /**
     * Filters events from the given stream to only include events for the specified promptId.
     *
     * @param events the event stream to filter
     * @param promptId the prompt ID to filter by
     * @return a filtered Flux of events for the specified prompt
     */
    public Flux<ComfyWebSocketEvent> filterEventsByPromptId(Flux<ComfyWebSocketEvent> events, String promptId) {
        return events.filter(event -> promptId.equals(event.promptId()));
    }

    public record ComfyJobResponse(
            String id,
            String status,
            PreviewOutput preview_output
    ) {
        public record PreviewOutput(
                String filename,
                String subfolder,
                String type
        ) {}
    }

    public Resource viewImageByPromptId(String promptId) {
        log.info("Fetching image by  prompt id: {}", promptId);

        ComfyJobResponse comfyJobResponse =  comfyUiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(API)
                        .pathSegment(JOBS)
                        .pathSegment(promptId)
                        .build())
                .retrieve()
                .bodyToMono(ComfyJobResponse.class)
                .block();

        assert comfyJobResponse != null;
        String filename = comfyJobResponse.preview_output().filename();

        return comfyUiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(VIEW, filename)
                        .build())
                .retrieve()
                .bodyToMono(Resource.class)
                .block();
    }

    private ComfyWorkflow loadWorkflowTemplate() {
        try {
            return jsonMapper.readValue(workflowFile.getInputStream(), ComfyWorkflow.class);
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load workflow template", ioException);
        }
    }
}
