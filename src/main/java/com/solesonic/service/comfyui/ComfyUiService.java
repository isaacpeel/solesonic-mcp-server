package com.solesonic.service.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyEvent;
import com.solesonic.model.comfyui.ComfyEvent.Disconnected;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionFailed;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionInterrupted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyFreeRequest;
import com.solesonic.model.comfyui.ComfyHistoryEntry;
import com.solesonic.model.comfyui.ComfyImageReference;
import com.solesonic.model.comfyui.ComfyNodeOutput;
import com.solesonic.model.comfyui.ComfyPromptRequest;
import com.solesonic.model.comfyui.ComfyPromptResponse;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ComfyUploadResponse;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.ReferenceImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.COMFY_UI_WEB_CLIENT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.FREE_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.HISTORY_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.INPUT_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_JPEG;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.MIME_TYPE_WEBP;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.OUTPUT_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_FIELD_IMAGE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_FIELD_OVERWRITE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_FIELD_SUBFOLDER;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_FIELD_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_FILENAME_PREFIX;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.UPLOAD_SUBFOLDER;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.PROMPT_ENDPOINT;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_FILENAME;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_SUBFOLDER;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.QUERY_PARAM_TYPE;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.STATUS_ERROR;
import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.VIEW_ENDPOINT;

/**
 * Drives ComfyUI's three-call protocol: submit a workflow, wait for the job to land in history,
 * then download the rendered image.
 *
 * <p>While the job runs, progress comes from ComfyUI's {@code /ws} event stream — queue position,
 * the node being run, sampler steps. If the stream cannot be opened or drops, the wait falls back to
 * polling {@code /history} with no percentage, so a socket problem costs the detail, never the image.
 */
@Service
public class ComfyUiService {

    private static final Logger log = LoggerFactory.getLogger(ComfyUiService.class);

    private static final ParameterizedTypeReference<Map<String, ComfyHistoryEntry>> HISTORY_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final int PROGRESS_UPLOADING = 3;
    private static final int PROGRESS_SUBMITTING = 5;
    private static final int PROGRESS_QUEUED = 15;
    private static final int PROGRESS_DOWNLOADING = 90;
    private static final int PROGRESS_COMPLETE = 100;

    private final WebClient webClient;
    private final ComfyUiEventStream comfyUiEventStream;
    private final long generationTimeoutSeconds;
    private final long pollIntervalMillis;
    private final Duration releaseMemoryDelay;

    private Disposable pendingMemoryRelease;

    public ComfyUiService(
            @Qualifier(COMFY_UI_WEB_CLIENT) WebClient webClient,
            ComfyUiEventStream comfyUiEventStream,
            @Value("${comfyui.generation.timeout-seconds}") long generationTimeoutSeconds,
            @Value("${comfyui.generation.poll-interval-millis}") long pollIntervalMillis,
            @Value("${comfyui.generation.release-memory-delay-millis}") long releaseMemoryDelayMillis
    ) {
        this.webClient = webClient;
        this.comfyUiEventStream = comfyUiEventStream;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
        this.pollIntervalMillis = pollIntervalMillis;
        this.releaseMemoryDelay = Duration.ofMillis(releaseMemoryDelayMillis);
    }

    /**
     * The workflow arrives per call rather than as a constructor dependency: each stored workflow is
     * its own MCP tool, so which template to patch is a property of the invocation.
     */
    public GeneratedImage generate(
            ComfyWorkflowTemplate comfyWorkflowTemplate,
            ImageGenerationRequest imageGenerationRequest,
            ProgressReporter progressReporter
    ) {
        log.info("Generating {}x{} image at seed {}",
                imageGenerationRequest.width(),
                imageGenerationRequest.height(),
                imageGenerationRequest.seed());

        List<ReferenceImage> referenceImages = imageGenerationRequest.referenceImages();

        if (referenceImages.size() != comfyWorkflowTemplate.referenceImageSlots()) {
            throw new ComfyUiException("ComfyUI workflow takes %d reference image(s); %d supplied"
                    .formatted(comfyWorkflowTemplate.referenceImageSlots(), referenceImages.size()));
        }

        long startNanos = System.nanoTime();

        List<String> referenceImageValues = uploadReferenceImages(referenceImages, progressReporter);

        ObjectNode workflow = comfyWorkflowTemplate.build(imageGenerationRequest, referenceImageValues);
        long deadlineNanos = startNanos + TimeUnit.SECONDS.toNanos(generationTimeoutSeconds);

        ComfyImageReference imageReference = submitAndAwaitImage(workflow, deadlineNanos, progressReporter);

        progressReporter.emit(PROGRESS_DOWNLOADING, "Downloading image");

        String base64Png = Base64.getEncoder().encodeToString(download(imageReference));

        scheduleReleaseMemory();

        double elapsedSeconds = elapsedSeconds(startNanos);

        progressReporter.emit(PROGRESS_COMPLETE, "Generated %d×%d in %.1fs (seed %d)"
                .formatted(
                        imageGenerationRequest.width(),
                        imageGenerationRequest.height(),
                        elapsedSeconds,
                        imageGenerationRequest.seed()));

        log.info("ComfyUI generation of {} completed in {}s", imageReference.filename(), elapsedSeconds);

        return GeneratedImage.imageGenerationRequest(imageGenerationRequest)
                .base64Png(base64Png)
                .elapsedSeconds(elapsedSeconds)
                .build();
    }

    /**
     * Debounces {@link #releaseMemory()}: each call cancels whatever release was previously
     * pending and schedules a new one {@code releaseMemoryDelay} out, so back-to-back generations
     * never force ComfyUI to reload the model between requests — {@code /free} only fires once
     * {@code releaseMemoryDelay} has passed since the *last* generation.
     *
     * <p>{@code synchronized} because {@code generate()} can run concurrently for different
     * requests: cancelling the previous timer and installing the new one must happen as one
     * atomic step, otherwise two racing calls can interleave such that the earlier call's timer
     * survives instead of the later one's.
     */
    private synchronized void scheduleReleaseMemory() {
        if (pendingMemoryRelease != null) {
            pendingMemoryRelease.dispose();
        }

        pendingMemoryRelease = Mono.delay(releaseMemoryDelay)
                .then(Mono.fromRunnable(this::releaseMemory))
                .subscribe();
    }

    /**
     * Fire-and-forget: ComfyUI keeps the generated model resident in VRAM between requests, so this
     * releases it now that the image is safely in hand. A failure here must never fail {@code
     * generate()} — the image was already produced successfully — so the response is neither
     * blocked on nor propagated, only logged.
     */
    private void releaseMemory() {
        webClient.post()
                .uri(FREE_ENDPOINT)
                .bodyValue(new ComfyFreeRequest(true, true))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ComfyUiException(
                                "ComfyUI /free failed with status " + clientResponse.statusCode(), body))))
                .bodyToMono(Void.class)
                .doOnError(error -> log.warn("ComfyUI /free call failed, memory was not released", error))
                .onErrorComplete()
                .subscribe();
    }

    /**
     * A {@code LoadImage} node takes the name of a file already in ComfyUI's {@code input/}
     * directory, never bytes, so each image is uploaded first and the name ComfyUI reports back —
     * which may differ from the one sent — is what the workflow is given.
     *
     * <p>Names are unique per call and never overwrite, so concurrent generations cannot load each
     * other's images.
     */
    private List<String> uploadReferenceImages(List<ReferenceImage> referenceImages, ProgressReporter progressReporter) {
        if (referenceImages.isEmpty()) {
            return List.of();
        }

        progressReporter.emit(PROGRESS_UPLOADING, "Uploading reference images");

        List<String> referenceImageValues = new ArrayList<>(referenceImages.size());

        for (ReferenceImage referenceImage : referenceImages) {
            referenceImageValues.add(upload(referenceImage));
        }

        return List.copyOf(referenceImageValues);
    }

    private String upload(ReferenceImage referenceImage) {
        MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
        multipartBodyBuilder.part(UPLOAD_FIELD_IMAGE, referenceImage.bytes())
                .filename(UPLOAD_FILENAME_PREFIX + UUID.randomUUID() + "." + extension(referenceImage.mimeType()))
                .contentType(MediaType.parseMediaType(referenceImage.mimeType()));
        multipartBodyBuilder.part(UPLOAD_FIELD_TYPE, INPUT_TYPE);
        multipartBodyBuilder.part(UPLOAD_FIELD_SUBFOLDER, UPLOAD_SUBFOLDER);
        multipartBodyBuilder.part(UPLOAD_FIELD_OVERWRITE, Boolean.FALSE.toString());

        ComfyUploadResponse comfyUploadResponse = webClient.post()
                .uri(UPLOAD_ENDPOINT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new ComfyUiException(
                                "ComfyUI image upload failed with status "
                                        + clientResponse.statusCode(), body))))
                .bodyToMono(ComfyUploadResponse.class)
                .block();

        if (comfyUploadResponse == null || comfyUploadResponse.name() == null) {
            throw new ComfyUiException("ComfyUI returned no file name for an image upload");
        }

        return comfyUploadResponse.loadImageValue();
    }

    private static String extension(String mimeType) {
        return switch (mimeType) {
            case MIME_TYPE_JPEG -> "jpg";
            case MIME_TYPE_WEBP -> "webp";
            default -> "png";
        };
    }

    /**
     * ComfyUI answers HTTP 200 with a populated {@code node_errors} object when the workflow is
     * malformed, so trusting the status code alone is the standard way to get a silent no-op.
     */
    private String submit(ObjectNode workflow, String clientId) {
        ComfyPromptRequest promptRequest = new ComfyPromptRequest(workflow, clientId);

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

    /**
     * The event stream is opened before the prompt is submitted: ComfyUI only sends a prompt's
     * events to a socket already registered under its {@code client_id}, so opening it afterwards
     * could miss a fast job's events entirely.
     */
    private ComfyImageReference submitAndAwaitImage(ObjectNode workflow, long deadlineNanos, ProgressReporter progressReporter) {
        String clientId = UUID.randomUUID().toString();
        Optional<ComfyEventSubscription> eventSubscription = openEventStream(clientId);

        try {
            progressReporter.emit(PROGRESS_SUBMITTING, "Sending prompt to ComfyUI");

            String promptId = submit(workflow, clientId);

            progressReporter.emit(PROGRESS_QUEUED, "Queued as " + promptId);

            ComfyImageReference imageReference = eventSubscription.isEmpty()
                    ? pollForImage(promptId, deadlineNanos, progressReporter)
                    : awaitImageFromEvents(promptId, workflow, eventSubscription.get(), deadlineNanos, progressReporter);

            log.info("ComfyUI generation {} rendered {}", promptId, imageReference.filename());

            return imageReference;
        } finally {
            eventSubscription.ifPresent(ComfyEventSubscription::close);
        }
    }

    /**
     * Any failure to open the stream — unreachable host, a proxy that does not pass WebSocket
     * upgrades, a timeout — only costs progress detail, so it is logged and the caller polls instead.
     */
    private Optional<ComfyEventSubscription> openEventStream(String clientId) {
        try {
            return Optional.of(comfyUiEventStream.open(clientId));
        } catch (RuntimeException runtimeException) {
            log.warn("ComfyUI event stream unavailable; polling history without step progress", runtimeException);
            return Optional.empty();
        }
    }

    /**
     * {@code executing} with a null node is the completion signal: ComfyUI sends it after writing the
     * history entry, unlike {@code execution_success}, which comes before. A quiet stream triggers a
     * {@code /history} check, so a missed frame delays the result by one interval rather than hanging
     * the call until the deadline.
     */
    private ComfyImageReference awaitImageFromEvents(
            String promptId,
            ObjectNode workflow,
            ComfyEventSubscription eventSubscription,
            long deadlineNanos,
            ProgressReporter progressReporter
    ) {
        ComfyProgressTracker comfyProgressTracker = new ComfyProgressTracker(workflow, promptId);

        while (true) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());

            if (remainingMillis <= 0) {
                throw timedOut(promptId);
            }

            Optional<ComfyEvent> nextEvent = eventSubscription.next(Duration.ofMillis(Math.min(pollIntervalMillis, remainingMillis)));

            if (nextEvent.isEmpty()) {
                Optional<ComfyImageReference> imageReference = imageFromHistory(promptId);

                if (imageReference.isPresent()) {
                    return imageReference.get();
                }

                continue;
            }

            switch (nextEvent.get()) {
                case Disconnected disconnected -> {
                    log.warn("ComfyUI event stream closed during generation {} ({}); polling history instead",
                            promptId, disconnected.reason());
                    return pollForImage(promptId, deadlineNanos, progressReporter);
                }
                case NodeExecuting nodeExecuting when promptId.equals(nodeExecuting.promptId()) && nodeExecuting.nodeId() == null -> {
                    return pollForImage(promptId, deadlineNanos, progressReporter);
                }
                case ExecutionFailed executionFailed when promptId.equals(executionFailed.promptId()) ->
                        throw new ComfyUiException("ComfyUI generation %s failed in %s: %s".formatted(
                                promptId,
                                describeNode(executionFailed.nodeType(), executionFailed.nodeId()),
                                executionFailed.exceptionMessage()));
                case ExecutionInterrupted executionInterrupted when promptId.equals(executionInterrupted.promptId()) ->
                        throw new ComfyUiException("ComfyUI generation %s was interrupted in %s".formatted(
                                promptId,
                                describeNode(executionInterrupted.nodeType(), executionInterrupted.nodeId())));
                case ComfyEvent comfyEvent -> comfyProgressTracker.apply(comfyEvent)
                        .ifPresent(progressUpdate -> progressReporter.emit(progressUpdate.percent(), progressUpdate.message()));
            }
        }
    }

    /**
     * The fallback when there is no event stream: a fixed message, and the percentage held where it
     * is, because there is nothing real to report.
     */
    private ComfyImageReference pollForImage(String promptId, long deadlineNanos, ProgressReporter progressReporter) {
        while (true) {
            Optional<ComfyImageReference> imageReference = imageFromHistory(promptId);

            if (imageReference.isPresent()) {
                return imageReference.get();
            }

            if (System.nanoTime() >= deadlineNanos) {
                throw timedOut(promptId);
            }

            progressReporter.emit(PROGRESS_QUEUED, "Generating…");

            sleepUntilNextPoll();
        }
    }

    /**
     * Empty while the job is pending or running; {@code /history} answers {@code {}} until it lands.
     */
    private Optional<ComfyImageReference> imageFromHistory(String promptId) {
        ComfyHistoryEntry historyEntry = fetchHistoryEntry(promptId);

        if (historyEntry == null) {
            return Optional.empty();
        }

        if (historyEntry.status() != null && STATUS_ERROR.equals(historyEntry.status().statusStr())) {
            throw new ComfyUiException("ComfyUI generation " + promptId + " failed during execution");
        }

        ComfyImageReference imageReference = firstImage(historyEntry);

        if (imageReference != null) {
            return Optional.of(imageReference);
        }

        if (historyEntry.status() != null && Boolean.TRUE.equals(historyEntry.status().completed())) {
            throw new ComfyUiException("ComfyUI generation " + promptId + " completed without producing an image");
        }

        return Optional.empty();
    }

    private ComfyUiException timedOut(String promptId) {
        return new ComfyUiException("ComfyUI generation " + promptId + " did not finish within "
                + generationTimeoutSeconds + "s");
    }

    private static String describeNode(String nodeType, String nodeId) {
        String type = nodeType == null ? "an unknown node" : nodeType;

        return nodeId == null ? type : "%s (node %s)".formatted(type, nodeId);
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
