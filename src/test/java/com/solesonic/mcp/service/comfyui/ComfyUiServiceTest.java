package com.solesonic.mcp.service.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.model.comfyui.ComfyNode;
import com.solesonic.mcp.model.comfyui.ComfyPrompt;
import com.solesonic.mcp.model.comfyui.ComfyWorkflow;
import com.solesonic.mcp.model.comfyui.ComfyWorkflowResponse;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutionStartEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComfyUiServiceTest {

    private ComfyUiService comfyUiService;

    @Mock
    private WebClient webClient;

    @Mock
    private ComfyUiWebSocketClient webSocketClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        comfyUiService = new ComfyUiService(objectMapper, webClient, webSocketClient);
        ReflectionTestUtils.setField(comfyUiService, "workflowFile", new ClassPathResource("comfyui/test-workflow.json"));
    }

    @Test
    void generateImage_shouldPostCorrectPayload() {
        String userPrompt = "A beautiful sunset over the mountains";

        ComfyWorkflowResponse expectedResponse = new ComfyWorkflowResponse();
        expectedResponse.setPromptId("123");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ComfyWorkflowResponse.class)).thenReturn(Mono.just(expectedResponse));

        ComfyWorkflowResponse actualResponse = comfyUiService.generateImage(userPrompt);

        assertNotNull(actualResponse);
        assertEquals("123", actualResponse.getPromptId());

        ArgumentCaptor<ComfyWorkflow> workflowCaptor = ArgumentCaptor.forClass(ComfyWorkflow.class);
        verify(requestBodySpec).bodyValue(workflowCaptor.capture());

        ComfyWorkflow capturedWorkflow = workflowCaptor.getValue();
        assertNotNull(capturedWorkflow);
        assertNotNull(capturedWorkflow.getPrompt());

        ComfyPrompt capturedPrompt = capturedWorkflow.getPrompt();
        assertNotNull(capturedPrompt.getNodes());

        ComfyNode textNode = capturedPrompt.getNodes().get("58");
        assertNotNull(textNode, "Node '58' should exist in the captured prompt");
        assertEquals(userPrompt, textNode.getInputs().get("text"));

        ComfyNode seedNode = capturedPrompt.getNodes().get("57:3");
        assertNotNull(seedNode, "Node '57:3' should exist in the captured prompt");
        assertNotNull(seedNode.getInputs().get("seed"));
    }

    @Test
    void generateImage_withClientId_shouldIncludeClientIdInPayload() {
        String userPrompt = "A beautiful sunset";
        UUID clientId = UUID.randomUUID();

        ComfyWorkflowResponse expectedResponse = new ComfyWorkflowResponse();
        expectedResponse.setPromptId("456");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ComfyWorkflowResponse.class)).thenReturn(Mono.just(expectedResponse));

        ComfyWorkflowResponse actualResponse = comfyUiService.generateImage(userPrompt, clientId);

        assertNotNull(actualResponse);
        assertEquals("456", actualResponse.getPromptId());

        ArgumentCaptor<ComfyWorkflow> workflowCaptor = ArgumentCaptor.forClass(ComfyWorkflow.class);
        verify(requestBodySpec).bodyValue(workflowCaptor.capture());

        ComfyWorkflow capturedWorkflow = workflowCaptor.getValue();
        assertEquals(clientId.toString(), capturedWorkflow.getClientId());
    }

    @Test
    void generateImage_withoutClientId_shouldNotIncludeClientIdInPayload() {
        String userPrompt = "A beautiful sunset";

        ComfyWorkflowResponse expectedResponse = new ComfyWorkflowResponse();
        expectedResponse.setPromptId("789");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ComfyWorkflowResponse.class)).thenReturn(Mono.just(expectedResponse));

        comfyUiService.generateImage(userPrompt);

        ArgumentCaptor<ComfyWorkflow> workflowCaptor = ArgumentCaptor.forClass(ComfyWorkflow.class);
        verify(requestBodySpec).bodyValue(workflowCaptor.capture());

        ComfyWorkflow capturedWorkflow = workflowCaptor.getValue();
        assertNull(capturedWorkflow.getClientId());
    }

    @Test
    void generateImage_shouldThrowWhenWorkflowMissingRequiredNodes() {
        String invalidWorkflowJson = "{\"prompt\": {\"other\": {\"inputs\": {}}}}";
        Resource invalidResource = new ByteArrayResource(invalidWorkflowJson.getBytes()) {
            @Override
            public String getFilename() {
                return "invalid-workflow.json";
            }
        };
        ReflectionTestUtils.setField(comfyUiService, "workflowFile", invalidResource);

        assertThrows(IllegalStateException.class, () -> comfyUiService.generateImage("test prompt"));
    }

    @Test
    void generateImage_shouldGenerateRandomSeed() {
        ComfyWorkflowResponse expectedResponse = new ComfyWorkflowResponse();
        expectedResponse.setPromptId("seed-test");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ComfyWorkflowResponse.class)).thenReturn(Mono.just(expectedResponse));

        comfyUiService.generateImage("test prompt");

        ArgumentCaptor<ComfyWorkflow> workflowCaptor = ArgumentCaptor.forClass(ComfyWorkflow.class);
        verify(requestBodySpec).bodyValue(workflowCaptor.capture());

        ComfyWorkflow capturedWorkflow = workflowCaptor.getValue();
        ComfyNode seedNode = capturedWorkflow.getPrompt().getNodes().get("57:3");
        Object seedValue = seedNode.getInputs().get("seed");

        assertNotNull(seedValue);
        assertInstanceOf(Long.class, seedValue);
        assertTrue((Long) seedValue > 0, "Seed should be a positive number");
    }

    @Test
    void establishWebSocket_shouldDelegateToWebSocketClient() {
        UUID clientId = UUID.randomUUID();
        Flux<ComfyWebSocketEvent> expectedFlux = Flux.empty();

        when(webSocketClient.openSession(clientId)).thenReturn(expectedFlux);

        Flux<ComfyWebSocketEvent> result = comfyUiService.establishWebSocket(clientId);

        assertEquals(expectedFlux, result);
        verify(webSocketClient).openSession(clientId);
    }

    @Test
    void establishWebSocket_shouldReturnFluxFromWebSocketClient() {
        UUID clientId = UUID.randomUUID();
        ComfyExecutionStartEvent testEvent = new ComfyExecutionStartEvent("test-prompt", 1000L, null);
        Flux<ComfyWebSocketEvent> expectedFlux = Flux.just(testEvent);

        when(webSocketClient.openSession(clientId)).thenReturn(expectedFlux);

        Flux<ComfyWebSocketEvent> result = comfyUiService.establishWebSocket(clientId);

        StepVerifier.create(result)
                .expectNext(testEvent)
                .verifyComplete();
        verify(webSocketClient).openSession(clientId);
    }

    @Test
    void closeWebSocket_shouldDelegateToWebSocketClient() {
        UUID clientId = UUID.randomUUID();

        comfyUiService.closeWebSocket(clientId);

        verify(webSocketClient).closeSession(clientId);
    }

    @Test
    void closeWebSocket_shouldCallWebSocketClientOnce() {
        UUID clientId = UUID.randomUUID();

        comfyUiService.closeWebSocket(clientId);
        comfyUiService.closeWebSocket(clientId);

        verify(webSocketClient, times(2)).closeSession(clientId);
    }

    @Test
    void filterEventsByPromptId_shouldFilterCorrectly() {
        String targetPromptId = "target-prompt";
        String otherPromptId = "other-prompt";

        ComfyExecutionStartEvent targetEvent = new ComfyExecutionStartEvent(targetPromptId, 1000L, null);
        ComfyExecutionStartEvent otherEvent = new ComfyExecutionStartEvent(otherPromptId, 1000L, null);

        Flux<ComfyWebSocketEvent> events = Flux.just(targetEvent, otherEvent);

        Flux<ComfyWebSocketEvent> filtered = comfyUiService.filterEventsByPromptId(events, targetPromptId);

        StepVerifier.create(filtered)
                .assertNext(event -> assertEquals(targetPromptId, event.promptId()))
                .verifyComplete();
    }

    @Test
    void filterEventsByPromptId_shouldReturnEmptyFluxWhenNoMatch() {
        String targetPromptId = "target-prompt";
        String otherPromptId = "other-prompt";

        ComfyExecutionStartEvent otherEvent = new ComfyExecutionStartEvent(otherPromptId, 1000L, null);

        Flux<ComfyWebSocketEvent> events = Flux.just(otherEvent);

        Flux<ComfyWebSocketEvent> filtered = comfyUiService.filterEventsByPromptId(events, targetPromptId);

        StepVerifier.create(filtered)
                .verifyComplete();
    }

    @Test
    void filterEventsByPromptId_shouldReturnAllMatchingEvents() {
        String targetPromptId = "target-prompt";

        ComfyExecutionStartEvent event1 = new ComfyExecutionStartEvent(targetPromptId, 1000L, null);
        ComfyExecutionStartEvent event2 = new ComfyExecutionStartEvent(targetPromptId, 2000L, null);

        Flux<ComfyWebSocketEvent> events = Flux.just(event1, event2);

        Flux<ComfyWebSocketEvent> filtered = comfyUiService.filterEventsByPromptId(events, targetPromptId);

        StepVerifier.create(filtered)
                .expectNext(event1)
                .expectNext(event2)
                .verifyComplete();
    }

    @Test
    void filterEventsByPromptId_shouldHandleEmptyFlux() {
        String targetPromptId = "target-prompt";

        Flux<ComfyWebSocketEvent> events = Flux.empty();

        Flux<ComfyWebSocketEvent> filtered = comfyUiService.filterEventsByPromptId(events, targetPromptId);

        StepVerifier.create(filtered)
                .verifyComplete();
    }
}
