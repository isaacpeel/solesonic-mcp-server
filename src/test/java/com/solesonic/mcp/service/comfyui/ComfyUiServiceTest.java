package com.solesonic.mcp.service.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.model.comfyui.ReferenceImage;
import com.solesonic.service.comfyui.ComfyUiService;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    private ComfyWorkflowTemplate comfyWorkflowTemplate;
    private WebClient webClient;
    private ProgressReporter progressReporter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        comfyWorkflowTemplate = ComfyWorkflowTemplate.parse(readFixture(), jsonMapper);

        webClient = WebClient.builder()
                .baseUrl("http://comfy.test")
                .exchangeFunction(dispatchingExchangeFunction())
                .build();

        progressReporter = new ProgressReporter((percent, message) -> {
            capturedPercents.add(percent);
            capturedMessages.add(message);
        });
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

    @Test
    void generate_happyPath_releasesMemoryAfterDownload() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_COMPLETE);

        service(180).generate(comfyWorkflowTemplate, request(), progressReporter);

        awaitFreeCallCount(1);
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
        awaitFreeCallCount(1);
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

        awaitFreeCallCount(1);
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
        return new ComfyUiService(webClient, generationTimeoutSeconds, 1L, 12.0, 50L);
    }

    /**
     * The release delay is real (50ms in tests, per {@code application-test.properties}), so
     * asserting on it requires polling rather than a synchronous check.
     */
    @SuppressWarnings("all")
    private void awaitFreeCallCount(int expected) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

        while (freeCallCount.get() != expected && System.nanoTime() < deadlineNanos) {
            Thread.onSpinWait();
        }

        assertThat(freeCallCount).hasValue(expected);
    }

    private ImageGenerationRequest request() {
        return new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 42L);
    }

    private String readFixture() {
        try (InputStream inputStream = new ClassPathResource("comfyui/flux1-schnell-test.json").getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalStateException("Unable to read the ComfyUI workflow fixture", ioException);
        }
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
