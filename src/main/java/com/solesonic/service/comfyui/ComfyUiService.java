package com.solesonic.service.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyHistoryEntry;
import com.solesonic.model.comfyui.ComfyImageReference;
import com.solesonic.model.comfyui.ComfyNodeOutput;
import com.solesonic.model.comfyui.ComfyPromptRequest;
import com.solesonic.model.comfyui.ComfyPromptResponse;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.COMFY_UI_WEB_CLIENT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.HISTORY_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.OUTPUT_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.PROMPT_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_FILENAME;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_SUBFOLDER;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.STATUS_ERROR;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.VIEW_ENDPOINT;

/**
 * Drives ComfyUI's three-call protocol: submit a workflow, poll history until the job lands, then
 * download the rendered image.
 */
@Service
public class ComfyUiService {

    private static final Logger log = LoggerFactory.getLogger(ComfyUiService.class);

    private static final ParameterizedTypeReference<Map<String, ComfyHistoryEntry>> HISTORY_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final int PROGRESS_SUBMITTING = 5;
    private static final int PROGRESS_QUEUED = 15;
    private static final int PROGRESS_GENERATING_CEILING = 85;
    private static final int PROGRESS_DOWNLOADING = 90;
    private static final int PROGRESS_COMPLETE = 100;

    private final WebClient webClient;
    private final ComfyWorkflowTemplate comfyWorkflowTemplate;
    private final long generationTimeoutSeconds;
    private final long pollIntervalMillis;
    private final double expectedSeconds;

    public ComfyUiService(
            @Qualifier(COMFY_UI_WEB_CLIENT) WebClient webClient,
            ComfyWorkflowTemplate comfyWorkflowTemplate,
            @Value("${comfyui.generation.timeout-seconds}") long generationTimeoutSeconds,
            @Value("${comfyui.generation.poll-interval-millis}") long pollIntervalMillis,
            @Value("${comfyui.generation.expected-seconds}") double expectedSeconds
    ) {
        this.webClient = webClient;
        this.comfyWorkflowTemplate = comfyWorkflowTemplate;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
        this.pollIntervalMillis = pollIntervalMillis;
        this.expectedSeconds = expectedSeconds;
    }

    public GeneratedImage generate(ImageGenerationRequest request, ProgressReporter progressReporter) {
        log.info("Generating {}x{} image with {} steps at seed {}",
                request.width(), request.height(), request.steps(), request.seed());

        long startNanos = System.nanoTime();

        progressReporter.emit(PROGRESS_SUBMITTING, "Sending prompt to ComfyUI");

        ObjectNode workflow = comfyWorkflowTemplate.build(
                request.prompt(), request.width(), request.height(), request.steps(), request.seed());

        String promptId = submit(workflow);

        progressReporter.emit(PROGRESS_QUEUED, "Queued as " + promptId);

        ComfyImageReference imageReference = awaitImage(promptId, startNanos, progressReporter);

        progressReporter.emit(PROGRESS_DOWNLOADING, "Downloading image");

        String base64Png = Base64.getEncoder().encodeToString(download(imageReference));

        double elapsedSeconds = elapsedSeconds(startNanos);

        progressReporter.emit(PROGRESS_COMPLETE, "Generated %d×%d in %.1fs (seed %d)"
                .formatted(request.width(), request.height(), elapsedSeconds, request.seed()));

        log.info("ComfyUI generation {} completed in {}s", promptId, elapsedSeconds);

        return new GeneratedImage(
                base64Png, request.width(), request.height(), request.steps(), request.seed(), elapsedSeconds);
    }

    /**
     * ComfyUI answers HTTP 200 with a populated {@code node_errors} object when the workflow is
     * malformed, so trusting the status code alone is the standard way to get a silent no-op.
     */
    private String submit(ObjectNode workflow) {
        ComfyPromptRequest promptRequest = new ComfyPromptRequest(workflow, UUID.randomUUID().toString());

        ComfyPromptResponse promptResponse = webClient.post()
                .uri(PROMPT_ENDPOINT)
                .bodyValue(promptRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ComfyUiException(
                                "ComfyUI rejected the workflow with status " + clientResponse.statusCode(), body))))
                .bodyToMono(ComfyPromptResponse.class)
                .block();

        if (promptResponse == null || promptResponse.promptId() == null) {
            throw new ComfyUiException("ComfyUI returned no prompt id for the submitted workflow");
        }

        if (promptResponse.nodeErrors() != null && !promptResponse.nodeErrors().isEmpty()) {
            throw new ComfyUiException(
                    "ComfyUI reported node errors for the submitted workflow", promptResponse.nodeErrors().toString());
        }

        return promptResponse.promptId();
    }

    private ComfyImageReference awaitImage(String promptId, long startNanos, ProgressReporter progressReporter) {
        long deadlineNanos = startNanos + TimeUnit.SECONDS.toNanos(generationTimeoutSeconds);

        while (true) {
            ComfyHistoryEntry historyEntry = fetchHistoryEntry(promptId);

            if (historyEntry != null) {
                if (historyEntry.status() != null && STATUS_ERROR.equals(historyEntry.status().statusStr())) {
                    throw new ComfyUiException("ComfyUI generation " + promptId + " failed during execution");
                }

                ComfyImageReference imageReference = firstImage(historyEntry);

                if (imageReference != null) {
                    return imageReference;
                }

                if (historyEntry.status() != null && Boolean.TRUE.equals(historyEntry.status().completed())) {
                    throw new ComfyUiException("ComfyUI generation " + promptId + " completed without producing an image");
                }
            }

            if (System.nanoTime() >= deadlineNanos) {
                throw new ComfyUiException("ComfyUI generation " + promptId + " did not finish within "
                        + generationTimeoutSeconds + "s");
            }

            progressReporter.emit(generatingPercent(startNanos), "Generating…");

            sleepUntilNextPoll();
        }
    }

    /**
     * A time-based estimate rather than real progress, capped below completion so it never claims
     * the image is ready early. {@code ProgressReporter} clamps to the previous maximum, so a ramp
     * that stalls is safe.
     */
    private int generatingPercent(long startNanos) {
        double ratio = expectedSeconds <= 0 ? 1.0 : elapsedSeconds(startNanos) / expectedSeconds;
        int ramped = PROGRESS_QUEUED + (int) (70.0 * ratio);

        return Math.min(ramped, PROGRESS_GENERATING_CEILING);
    }

    private void sleepUntilNextPoll() {
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ComfyUiException("Interrupted while waiting for ComfyUI generation", interruptedException);
        }
    }

    private ComfyHistoryEntry fetchHistoryEntry(String promptId) {
        Map<String, ComfyHistoryEntry> history = webClient.get()
                .uri(HISTORY_ENDPOINT, promptId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ComfyUiException(
                                "ComfyUI history lookup for " + promptId + " failed with status "
                                        + clientResponse.statusCode(), body))))
                .bodyToMono(HISTORY_TYPE)
                .block();

        if (history == null || history.isEmpty()) {
            return null;
        }

        return history.get(promptId);
    }

    private ComfyImageReference firstImage(ComfyHistoryEntry historyEntry) {
        if (historyEntry.outputs() == null) {
            return null;
        }

        for (ComfyNodeOutput nodeOutput : historyEntry.outputs().values()) {
            if (nodeOutput != null && nodeOutput.images() != null && !nodeOutput.images().isEmpty()) {
                return nodeOutput.images().getFirst();
            }
        }

        return null;
    }

    private byte[] download(ComfyImageReference imageReference) {
        byte[] imageBytes = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(VIEW_ENDPOINT)
                        .queryParam(QUERY_PARAM_FILENAME, imageReference.filename())
                        .queryParam(QUERY_PARAM_SUBFOLDER, imageReference.subfolder() == null ? "" : imageReference.subfolder())
                        .queryParam(QUERY_PARAM_TYPE, imageReference.type() == null ? OUTPUT_TYPE : imageReference.type())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ComfyUiException(
                                "ComfyUI image download for " + imageReference.filename() + " failed with status "
                                        + clientResponse.statusCode(), body))))
                .bodyToMono(byte[].class)
                .block();

        if (imageBytes == null || imageBytes.length == 0) {
            throw new ComfyUiException("ComfyUI returned an empty image for " + imageReference.filename());
        }

        return imageBytes;
    }

    private double elapsedSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
