package com.solesonic.mcp.service.comfyui;

import com.solesonic.a2a.progress.ProgressReporter;
import com.solesonic.mcp.exception.comfyui.ComfyUiException;
import com.solesonic.model.comfyui.GeneratedImage;
import com.solesonic.model.comfyui.ImageGenerationRequest;
import com.solesonic.service.comfyui.ComfyUiService;
import com.solesonic.service.comfyui.ComfyWorkflowTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
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

    private final Deque<String> promptResponses = new ArrayDeque<>();
    private final Deque<String> historyResponses = new ArrayDeque<>();
    private final AtomicInteger historyCallCount = new AtomicInteger();

    private final List<Integer> capturedPercents = new ArrayList<>();
    private final List<String> capturedMessages = new ArrayList<>();

    private ComfyWorkflowTemplate comfyWorkflowTemplate;
    private WebClient webClient;
    private ProgressReporter progressReporter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        comfyWorkflowTemplate = new ComfyWorkflowTemplate(
                new ClassPathResource("comfyui/flux1-schnell-test.json"), jsonMapper);

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

        GeneratedImage generatedImage = service(180).generate(request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(generatedImage.width()).isEqualTo(1024);
        assertThat(generatedImage.height()).isEqualTo(1024);
        assertThat(generatedImage.steps()).isEqualTo(4);
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

        Throwable thrown = catchThrowable(() -> service(180).generate(request(), progressReporter));

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

        GeneratedImage generatedImage = service(180).generate(request(), progressReporter);

        assertThat(generatedImage.base64Png()).isEqualTo(Base64.getEncoder().encodeToString(PNG_BYTES));
        assertThat(historyCallCount).hasValue(3);
        assertThat(capturedMessages).contains("Generating…");
    }

    @Test
    void generate_deadlineExceeded_throwsNamingThePromptId() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_PENDING);

        assertThatThrownBy(() -> service(0).generate(request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining(PROMPT_ID)
                .hasMessageContaining("did not finish");
    }

    @Test
    void generate_executionStatusError_throws() {
        promptResponses.add(PROMPT_ACCEPTED);
        historyResponses.add(HISTORY_FAILED);

        assertThatThrownBy(() -> service(180).generate(request(), progressReporter))
                .isInstanceOf(ComfyUiException.class)
                .hasMessageContaining("failed during execution");
    }

    private ComfyUiService service(long generationTimeoutSeconds) {
        return new ComfyUiService(webClient, comfyWorkflowTemplate, generationTimeoutSeconds, 1L, 12.0);
    }

    private ImageGenerationRequest request() {
        return new ImageGenerationRequest("a lighthouse in a storm", 1024, 1024, 4, 42L);
    }

    /**
     * Stands in for the ComfyUI origin, dispatching on the request path so a single WebClient can
     * serve all three calls of the protocol.
     */
    private ExchangeFunction dispatchingExchangeFunction() {
        return clientRequest -> {
            String path = clientRequest.url().getPath();

            if (path.startsWith("/prompt")) {
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

            return Mono.error(new IllegalStateException("Unexpected ComfyUI request path: " + path));
        };
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body == null ? "{}" : body)
                .build();
    }

    private ClientResponse binaryResponse() {
        return ClientResponse.create(HttpStatus.OK)
                .header(CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(PNG_BYTES)))
                .build();
    }
}
