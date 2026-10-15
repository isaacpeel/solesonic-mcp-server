package com.solesonic.mcp.service.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.ComfyEvent;
import com.solesonic.model.comfyui.ComfyEvent.Disconnected;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionFailed;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionInterrupted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionStarted;
import com.solesonic.model.comfyui.ComfyEvent.ExecutionSucceeded;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuted;
import com.solesonic.model.comfyui.ComfyEvent.NodeExecuting;
import com.solesonic.model.comfyui.ComfyEvent.NodeProgress;
import com.solesonic.model.comfyui.ComfyEvent.QueueStatus;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.ReferenceImage;
import com.solesonic.service.comfyui.ComfyEventSubscription;
import com.solesonic.service.comfyui.ComfyUiEventStream;
import com.solesonic.service.comfyui.ComfyUiService;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

class ComfyUiServiceTest {

    private static final String PROMPT_ID = "abc-123";

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static final String PROMPT_ACCEPTED = """
            {"prompt_id":"abc-123","number":1,"node_errors":{}}""";

    private static final String PROMPT_WITH_NODE_ERRORS = """
            {"prompt_id":"abc-123","number":1,"node_errors":{"6":{"errors":["required input is missing"]}}}""";

    private static final String HISTORY_PENDING = "{}";

    private static final String HISTORY_COMPLETE = """
            {"abc-123":{"outputs":{"9":{"images":[{"filename":"ComfyUI_00001_.png","subfolder":"","type":"output"}]}},
            "status":{"status_str":"success","completed":true}}}""";

    private static final String HISTORY_FAILED = """
            {"abc-123":{"outputs":{},"status":{"status_str":"error","completed":true}}}""";

    private static final String UPLOAD_ACCEPTED_FIRST = """
            {"name":"mcp-first.png","subfolder":"mcp-server","type":"input"}""";

    private static final String UPLOAD_ACCEPTED_SECOND = """
            {"name":"mcp-second.png","subfolder":"mcp-server","type":"input"}""";

    private static final String TEXT_TO_IMAGE_WORKFLOW = """
            {"4": {"class_type": "CheckpointLoaderSimple", "inputs": {}, "_meta": {"title": "Load Checkpoint"}},
             "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "__PROMPT__"}},
             "3": {"class_type": "KSampler", "inputs": {"seed": "__SEED__"}},
             "8": {"class_type": "VAEDecode", "inputs": {}},
             "9": {"class_type": "SaveImage", "inputs": {}}}""";

    private static final String TWO_SLOT_VARIANT = """
            {"6": {"inputs": {"text": "__PROMPT__"}},
             "31": {"inputs": {"seed": "__SEED__"}},
             "40": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_1__"}},
             "41": {"class_type": "LoadImage", "inputs": {"image": "__REFERENCE_IMAGE_2__"}}}""";

    private final Deque<String> uploadResponses = new ArrayDeque<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();
    private final List<String> uploadBodies = new ArrayList<>();
    private final List<String> promptBodies = new ArrayList<>();
    private boolean uploadErrors;

    private final Deque<String> promptResponses = new ArrayDeque<>();
    private final Deque<String> historyResponses = new ArrayDeque<>();
    private final AtomicInteger historyCallCount = new AtomicInteger();
    private final AtomicInteger freeCallCount = new AtomicInteger();
    private boolean freeEndpointErrors;

    private final List<Integer> capturedPercents = new ArrayList<>();
    private final List<String> capturedMessages = new ArrayList<>();

    private final List<String> openedClientIds = new ArrayList<>();
    private final AtomicInteger closedSubscriptionCount = new AtomicInteger();

    private ComfyWorkflowTemplate comfyWorkflowTemplate;
    private WebClient webClient;
    private ProgressReporter progressReporter;

    /**
     * Unavailable unless a test scripts events, so the polling tests exercise the fallback path.
     */
    private ComfyUiEventStream comfyUiEventStream;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(TEXT_TO_IMAGE_WORKFLOW, jsonMapper);

        webClient = WebClient.builder()
                .baseUrl("http://comfy.test")
                .exchangeFunction(dispatchingExchangeFunction())
                .build();

        progressReporter = new ProgressReporter((percent, message) -> {
            capturedPercents.add(percent);
            capturedMessages.add(message);
        });

        comfyUiEventStream = _ -> {
            throw new ComfyUiException("event stream unavailable in this test");
        };
    }

    @Test
    void generate_happyPath_returnsBase64ImageAndEchoesResolvedParameters() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(generatedImage.width()).isEqualTo(1024);
        assertThat(generatedImage.height()).isEqualTo(1024);
        assertThat(generatedImage.seed()).isEqualTo(42L);
        assertThat(generatedImage.elapsedSeconds()).isPositive();

        assertThat(capturedPercents.getLast()).isEqualTo(100);
        assertThat(capturedMessages).anyMatch(message -> message.contains("Queued as " + PROMPT_ID));
    }

    /**
     * ComfyUI answers 200 with a populated {@code node_errors} on a malformed workflow, so the
     * status code alone would report a silent no-op as success.
     */
    @Test
    void generate_nodeErrorsOnHttp200_throws() {
        promptResponses.add(PROMPT_WITH_NODE_ERRORS);

        Throwable thrown = catchThrowable(() -> service(180).generate(comfyWorkflowTemplate, request(), progressReporter));

        assertThat(thrown).isInstanceOf(ComfyUiException.class).hasMessageContaining("node errors");
        assertThat(((ComfyUiException) thrown).getRawResponse()).contains("required input is missing");
        assertThat(historyCallCount).hasValue(0);
    }

    @Test
    void generate_historyEmptyThenPopulated_pollsUntilTheJobLands() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_COMPLETE);

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(historyCallCount).hasValue(3);
        assertThat(capturedMessages).contains("Generating…");
    }

    /**
     * Without the event stream there is nothing real to report, so polling never moves the
     * percentage past the queued step.
     */
    @Test
    void generate_eventStreamUnavailable_pollsWithoutRampingThePercentage() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_COMPLETE);

        service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(percentsFor("Generating…")).isNotEmpty().allMatch(percent -> percent == 15);
    }

    @Test
    void generate_deadlineExceeded_throwsNamingThePromptId() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);

        assertThatThrownBy(() -> service(0).generate(comfyWorkflowTemplate, request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining(PROMPT_ID)
                .hasMessageContaining("did not finish");
    }

    @Test
    void generate_executionStatusError_throws() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_FAILED);

        assertThatThrownBy(() -> service(180).generate(comfyWorkflowTemplate, request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("failed during execution");
    }

    /**
     * The prompt must carry the client id the event stream was opened under, or ComfyUI sends its
     * events to nobody.
     */
    @Test
    void generate_eventStream_submitsWithTheStreamsClientId() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        comfyUiEventStream = scriptedEventStream(new NodeExecuting(PROMPT_ID, null));

        service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(openedClientIds).hasSize(1);
        assertThat(promptBodies.getFirst()).contains("\"client_id\":\"" + openedClientIds.getFirst() + "\"");
    }

    @Test
    void generate_eventStream_reportsQueueNodesAndStepsFromComfyUi() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        comfyUiEventStream = scriptedEventStream(
                new QueueStatus(2),
                new ExecutionStarted(PROMPT_ID),
                new NodeExecuting(PROMPT_ID, "4"),
                new NodeExecuted(PROMPT_ID, "4"),
                new NodeExecuting(PROMPT_ID, "6"),
                new NodeExecuted(PROMPT_ID, "6"),
                new NodeExecuting(PROMPT_ID, "3"),
                new NodeProgress(PROMPT_ID, "3", 15, 30),
                new NodeProgress(PROMPT_ID, "3", 30, 30),
                new NodeExecuted(PROMPT_ID, "3"),
                new NodeExecuting(PROMPT_ID, "8"),
                new NodeExecuted(PROMPT_ID, "8"),
                new NodeExecuting(PROMPT_ID, "9"),
                new NodeExecuted(PROMPT_ID, "9"),
                new ExecutionSucceeded(PROMPT_ID),
                new NodeExecuting(PROMPT_ID, null));

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(capturedMessages).contains(
                "Waiting in queue (1 ahead)",
                "Running Load Checkpoint",
                "Running KSampler",
                "KSampler: step 15/30",
                "Finishing",
                "Downloading image");
        assertThat(capturedMessages).doesNotContain("Generating…");
        assertThat(percentsFor("KSampler: step 15/30")).containsExactly(50);
        assertThat(percentsFor("Finishing")).containsExactly(85);
        assertThat(capturedPercents).isSorted();
        assertThat(historyCallCount).hasValue(1);
    }

    @Test
    void generate_executionError_throwsNamingTheNodeAndMessageWithoutReadingHistory() {
        promptResponses.add(PROMPT_ACCEPTED);
        comfyUiEventStream = scriptedEventStream(
                new ExecutionStarted(PROMPT_ID),
                new NodeExecuting(PROMPT_ID, "3"),
                new ExecutionFailed(PROMPT_ID, "3", "KSampler", "Allocation on device failed"));

        assertThatThrownBy(() -> service(180).generate(comfyWorkflowTemplate, request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining(PROMPT_ID)
                .hasMessageContaining("KSampler (node 3)")
                .hasMessageContaining("Allocation on device failed");

        assertThat(historyCallCount).hasValue(0);
        assertThat(closedSubscriptionCount).hasValue(1);
    }

    @Test
    void generate_interrupted_throws() {
        promptResponses.add(PROMPT_ACCEPTED);
        comfyUiEventStream = scriptedEventStream(
                new NodeExecuting(PROMPT_ID, "3"),
                new ExecutionInterrupted(PROMPT_ID, "3", "KSampler"));

        assertThatThrownBy(() -> service(180).generate(comfyWorkflowTemplate, request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("interrupted")
                .hasMessageContaining("KSampler");
    }

    /**
     * Another prompt's end-of-run signal must not end this one: the call keeps waiting until its own
     * job lands in history.
     */
    @Test
    void generate_otherPromptsEvents_areIgnored() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_COMPLETE);
        comfyUiEventStream = scriptedEventStream(
                new NodeExecuting("other-prompt", null),
                new ExecutionFailed("other-prompt", "3", "KSampler", "not ours"));

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(historyCallCount).hasValue(2);
    }

    @Test
    void generate_streamDisconnectsMidRun_fallsBackToPollingHistory() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);
        historyResponses.add(HISTORY_COMPLETE);
        comfyUiEventStream = scriptedEventStream(
                new NodeExecuting(PROMPT_ID, "3"),
                new NodeProgress(PROMPT_ID, "3", 10, 30),
                new Disconnected("connection reset"));

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(capturedMessages).contains("KSampler: step 10/30", "Generating…");
        assertThat(capturedPercents).isSorted();
        assertThat(closedSubscriptionCount).hasValue(1);
    }

    /**
     * A missed completion frame must not hang the call: a quiet stream triggers a history check.
     */
    @Test
    void generate_quietEventStream_findsTheImageInHistory() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        comfyUiEventStream = scriptedEventStream();

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(historyCallCount).hasValue(1);
    }

    @Test
    void generate_quietEventStreamPastTheDeadline_throws() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);
        comfyUiEventStream = scriptedEventStream();

        assertThatThrownBy(() -> service(0).generate(comfyWorkflowTemplate, request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("did not finish");

        assertThat(closedSubscriptionCount).hasValue(1);
    }

    @Test
    void generate_happyPath_releasesMemoryAfterDownload() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);

        service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        awaitOneFreeCall();
    }

    /**
     * The {@code /free} call is fire-and-forget: a failure there must never surface to the caller,
     * since it would otherwise turn a successful generation into a reported failure.
     */
    @Test
    void generate_freeEndpointErrors_stillReturnsImage() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        freeEndpointErrors = true;

        GeneratedImage generatedImage = service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        awaitOneFreeCall();
    }

    /**
     * {@code /free} is debounced: a generation that lands before the previous release delay
     * elapses must cancel and reschedule it, rather than letting both fire.
     */
    @Test
    void generate_calledTwiceQuickly_releasesMemoryOnlyOnceAfterLastCall() {
        promptResponses.add(PROMPT_ACCEPTED);
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        historyResponses.add(HISTORY_COMPLETE);

        ComfyUiService comfyUiService = service(180);
        comfyUiService.generate(comfyWorkflowTemplate, request(), progressReporter);
        comfyUiService.generate(comfyWorkflowTemplate, request(), progressReporter);

        awaitOneFreeCall();
    }

    @Test
    void generate_withoutReferenceImages_neverUploads() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);

        service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        assertThat(requestPaths).noneMatch(path -> path.startsWith("/upload"));
    }

    /**
     * A {@code LoadImage} node names a file that must already exist in ComfyUI's input directory, so
     * every upload has to land before the workflow that refers to it is submitted.
     */
    @Test
    void generate_withReferenceImages_uploadsEachBeforeSubmittingTheVariantGraph() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);
        uploadResponses.add(UPLOAD_ACCEPTED_FIRST);
        uploadResponses.add(UPLOAD_ACCEPTED_SECOND);

        ComfyWorkflowTemplate variant = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, JsonMapper.builder().build());

        service(180).generate(variant, referenceRequest(2), progressReporter);

        List<String> callOrder = requestPaths.stream()
                .filter(path -> path.startsWith("/upload") || path.startsWith("/prompt"))
                .toList();
        assertThat(callOrder).containsExactly("/upload/image", "/upload/image", "/prompt");

        assertThat(uploadBodies).hasSize(2).allSatisfy(body -> {
            assertThat(body).contains("name=\"image\"; filename=\"mcp-");
            assertThat(body).contains("name=\"type\"").contains("input");
            assertThat(body).contains("name=\"subfolder\"").contains("mcp-server");
            assertThat(body).contains("Content-Type: image/png");
        });

        String submitted = promptBodies.getFirst();
        assertThat(submitted).contains("\"image\":\"mcp-server/mcp-first.png\"");
        assertThat(submitted).contains("\"image\":\"mcp-server/mcp-second.png\"");
        assertThat(submitted).doesNotContain("__REFERENCE_IMAGE_");
        assertThat(capturedMessages).contains("Uploading reference images");
    }

    @Test
    void generate_uploadRejected_throwsWithoutSubmitting() {
        uploadErrors = true;

        ComfyWorkflowTemplate variant = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, JsonMapper.builder().build());

        assertThatThrownBy(() -> service(180).generate(variant, referenceRequest(2), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("upload");

        assertThat(requestPaths).noneMatch(path -> path.startsWith("/prompt"));
    }

    @Test
    void generate_imageCountDiffersFromTheTemplateSlots_throwsBeforeAnyCall() {
        ComfyWorkflowTemplate variant = ComfyWorkflowTemplate.parse(TWO_SLOT_VARIANT, JsonMapper.builder().build());

        assertThatThrownBy(() -> service(180).generate(variant, referenceRequest(1), progressReporter))
                .isInstanceOf(ComfyUiException.class);

        assertThat(requestPaths).isEmpty();
    }

    /**
     * Every event is queued before the prompt is submitted, which the service then reads in order —
     * the same order a live stream would deliver them.
     */
    private ComfyUiEventStream scriptedEventStream(ComfyEvent... comfyEvents) {
        return clientId -> {
            openedClientIds.add(clientId);
            ComfyEventSubscription comfyEventSubscription = new ComfyEventSubscription(closedSubscriptionCount::incrementAndGet);

            for (ComfyEvent comfyEvent : comfyEvents) {
                comfyEventSubscription.publish(comfyEvent);
            }

            return comfyEventSubscription;
        };
    }

    private List<Integer> percentsFor(String message) {
        List<Integer> percents = new ArrayList<>();

        for (int index = 0; index < capturedMessages.size(); index++) {
            if (capturedMessages.get(index).equals(message)) {
                percents.add(capturedPercents.get(index));
            }
        }

        return percents;
    }

    private ImageGenerationRequest referenceRequest(int imageCount) {
        List<ReferenceImage> referenceImages = new ArrayList<>();

        for (int index = 0; index < imageCount; index++) {
            referenceImages.add(new ReferenceImage(PNG_BYTES, "image/png"));
        }

        return new ImageGenerationRequest("make it a watercolor", 1024, 1024, 42L, referenceImages);
    }

    private String bodyOf(ClientRequest clientRequest) {
        MockClientHttpRequest mockClientHttpRequest = new MockClientHttpRequest(clientRequest.method(), clientRequest.url());
        clientRequest.writeTo(mockClientHttpRequest, ExchangeStrategies.withDefaults()).block();

        return mockClientHttpRequest.getBodyAsString().block();
    }

    private ComfyUiService service(long generationTimeoutSeconds) {
        return new ComfyUiService(webClient, comfyUiEventStream, generationTimeoutSeconds, 1L, 50L);
    }

    /**
     * The release delay is real (50ms in tests, per {@code application-test.properties}), so
     * asserting on it requires polling rather than a synchronous check.
     */
    private void awaitOneFreeCall() {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

        while (freeCallCount.get() != 1 && System.nanoTime() < deadlineNanos) {
            Thread.onSpinWait();
        }

        assertThat(freeCallCount).hasValue(1);
    }

    private ImageGenerationRequest request() {
        return new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 42L);
    }

    /**
     * Stands in for the ComfyUI origin, dispatching on the request path so a single WebClient can
     * serve all three calls of the protocol.
     */
    private ExchangeFunction dispatchingExchangeFunction() {
        return clientRequest -> {
            String path = clientRequest.url().getPath();
            requestPaths.add(path);

            if (path.startsWith("/upload")) {
                uploadBodies.add(bodyOf(clientRequest));
                return Mono.just(uploadErrors ? badRequestResponse() : jsonResponse(uploadResponses.poll()));
            }

            if (path.startsWith("/prompt")) {
                promptBodies.add(bodyOf(clientRequest));
                return Mono.just(jsonResponse(promptResponses.poll()));
            }

            if (path.startsWith("/history")) {
                historyCallCount.incrementAndGet();
                String body = historyResponses.size() > 1 ? historyResponses.poll() : historyResponses.peek();
                return Mono.just(jsonResponse(body));
            }

            if (path.startsWith("/view")) {
                return Mono.just(binaryResponse());
            }

            if (path.startsWith("/free")) {
                freeCallCount.incrementAndGet();
                return Mono.just(freeEndpointErrors ? errorResponse() : jsonResponse("{}"));
            }

            return Mono.error(new IllegalStateException("Unexpected ComfyUI request path: " + path));
        };
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body == null ? "{}" : body)
                .build();
    }

    private ClientResponse errorResponse() {
        return ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
                .build();
    }

    private ClientResponse badRequestResponse() {
        return ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body("Invalid image file")
                .build();
    }

    private ClientResponse binaryResponse() {
        return ClientResponse.create(HttpStatus.OK)
                .header(CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(PNG_BYTES)))
                .build();
    }
}
