package com.solesonic.mcp.service.comfyui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutingEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutionCachedEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutionErrorEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutionStartEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyExecutionSuccessEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyProgressEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyStatusEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyUnknownEvent;
import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComfyWebSocketEventParserTest {

    private ComfyWebSocketEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new ComfyWebSocketEventParser(new ObjectMapper());
    }

    @Test
    void parse_statusEvent_shouldReturnComfyStatusEvent() {
        String json = """
                {
                    "type": "status",
                    "data": {
                        "status": {
                            "exec_info": {
                                "queue_remaining": 3
                            }
                        }
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyStatusEvent.class, event);
        ComfyStatusEvent statusEvent = (ComfyStatusEvent) event;
        assertEquals("status", statusEvent.eventType());
        assertEquals(3, statusEvent.queueRemaining());
    }

    @Test
    void parse_executionStartEvent_shouldReturnComfyExecutionStartEvent() {
        String json = """
                {
                    "type": "execution_start",
                    "data": {
                        "prompt_id": "abc-123",
                        "timestamp": 1234567890
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutionStartEvent.class, event);
        ComfyExecutionStartEvent startEvent = (ComfyExecutionStartEvent) event;
        assertEquals("execution_start", startEvent.eventType());
        assertEquals("abc-123", startEvent.promptId());
        assertEquals(1234567890L, startEvent.timestamp());
    }

    @Test
    void parse_executionCachedEvent_shouldReturnComfyExecutionCachedEvent() {
        String json = """
                {
                    "type": "execution_cached",
                    "data": {
                        "prompt_id": "abc-123",
                        "nodes": ["81", "82", "94"],
                        "timestamp": 1234567890
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutionCachedEvent.class, event);
        ComfyExecutionCachedEvent cachedEvent = (ComfyExecutionCachedEvent) event;
        assertEquals("execution_cached", cachedEvent.eventType());
        assertEquals("abc-123", cachedEvent.promptId());
        assertEquals(3, cachedEvent.cachedNodes().size());
        assertTrue(cachedEvent.cachedNodes().contains("81"));
        assertTrue(cachedEvent.cachedNodes().contains("82"));
        assertTrue(cachedEvent.cachedNodes().contains("94"));
    }

    @Test
    void parse_executingEvent_shouldReturnComfyExecutingEvent() {
        String json = """
                {
                    "type": "executing",
                    "data": {
                        "prompt_id": "abc-123",
                        "node": "93"
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutingEvent.class, event);
        ComfyExecutingEvent executingEvent = (ComfyExecutingEvent) event;
        assertEquals("executing", executingEvent.eventType());
        assertEquals("abc-123", executingEvent.promptId());
        assertEquals("93", executingEvent.node());
    }

    @Test
    void parse_executingEventWithNullNode_shouldIndicateCompletion() {
        String json = """
                {
                    "type": "executing",
                    "data": {
                        "prompt_id": "abc-123",
                        "node": null
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutingEvent.class, event);
        ComfyExecutingEvent executingEvent = (ComfyExecutingEvent) event;
        assertNull(executingEvent.node());
        assertTrue(executingEvent.isExecutionComplete());
    }

    @Test
    void parse_progressEvent_shouldReturnComfyProgressEvent() {
        String json = """
                {
                    "type": "progress",
                    "data": {
                        "prompt_id": "abc-123",
                        "node": "93",
                        "value": 25,
                        "max": 50
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyProgressEvent.class, event);
        ComfyProgressEvent progressEvent = (ComfyProgressEvent) event;
        assertEquals("progress", progressEvent.eventType());
        assertEquals("abc-123", progressEvent.promptId());
        assertEquals("93", progressEvent.node());
        assertEquals(25, progressEvent.currentStep());
        assertEquals(50, progressEvent.totalSteps());
        assertEquals(50, progressEvent.progressPercent());
    }

    @Test
    void parse_executionSuccessEvent_shouldReturnComfyExecutionSuccessEvent() {
        String json = """
                {
                    "type": "execution_success",
                    "data": {
                        "prompt_id": "abc-123",
                        "timestamp": 1234567890
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutionSuccessEvent.class, event);
        ComfyExecutionSuccessEvent successEvent = (ComfyExecutionSuccessEvent) event;
        assertEquals("execution_success", successEvent.eventType());
        assertEquals("abc-123", successEvent.promptId());
        assertEquals(1234567890L, successEvent.timestamp());
    }

    @Test
    void parse_executionErrorEvent_shouldReturnComfyExecutionErrorEvent() {
        String json = """
                {
                    "type": "execution_error",
                    "data": {
                        "prompt_id": "abc-123",
                        "exception_message": "Out of memory",
                        "exception_type": "RuntimeError"
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyExecutionErrorEvent.class, event);
        ComfyExecutionErrorEvent errorEvent = (ComfyExecutionErrorEvent) event;
        assertEquals("execution_error", errorEvent.eventType());
        assertEquals("abc-123", errorEvent.promptId());
        assertEquals("Out of memory", errorEvent.errorMessage());
        assertEquals("RuntimeError", errorEvent.exceptionType());
    }

    @Test
    void parse_unknownEventType_shouldReturnComfyUnknownEvent() {
        String json = """
                {
                    "type": "some_new_event",
                    "data": {
                        "prompt_id": "abc-123",
                        "some_field": "some_value"
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyUnknownEvent.class, event);
        ComfyUnknownEvent unknownEvent = (ComfyUnknownEvent) event;
        assertEquals("some_new_event", unknownEvent.eventType());
        assertEquals("abc-123", unknownEvent.promptId());
        assertNotNull(unknownEvent.rawPayload());
    }

    @Test
    void parse_invalidJson_shouldReturnUnknownEvent() {
        String invalidJson = "not valid json";

        ComfyWebSocketEvent event = parser.parse(invalidJson);

        assertInstanceOf(ComfyUnknownEvent.class, event);
        ComfyUnknownEvent unknownEvent = (ComfyUnknownEvent) event;
        assertEquals("parse_error", unknownEvent.eventType());
    }

    @Test
    void parse_missingTypeField_shouldReturnUnknownEvent() {
        String json = """
                {
                    "data": {
                        "prompt_id": "abc-123"
                    }
                }
                """;

        ComfyWebSocketEvent event = parser.parse(json);

        assertInstanceOf(ComfyUnknownEvent.class, event);
        ComfyUnknownEvent unknownEvent = (ComfyUnknownEvent) event;
        assertEquals("missing_type", unknownEvent.eventType());
    }
}
