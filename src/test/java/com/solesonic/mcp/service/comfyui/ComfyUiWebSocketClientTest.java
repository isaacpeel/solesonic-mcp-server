package com.solesonic.mcp.service.comfyui;

import com.solesonic.mcp.model.comfyui.websocket.ComfyWebSocketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ComfyUiWebSocketClientTest {

    private ComfyUiWebSocketClient webSocketClient;

    @Mock
    private ComfyWebSocketEventParser eventParser;

    @BeforeEach
    void setUp() {
        webSocketClient = new ComfyUiWebSocketClient("http://localhost:8188", eventParser);
    }

    @Test
    void openSession_shouldReturnFlux() {
        UUID clientId = UUID.randomUUID();

        Flux<ComfyWebSocketEvent> eventFlux = webSocketClient.openSession(clientId);

        assertNotNull(eventFlux);
    }

    @Test
    void openSession_sameclientId_shouldReturnSameSession() {
        UUID clientId = UUID.randomUUID();

        Flux<ComfyWebSocketEvent> firstFlux = webSocketClient.openSession(clientId);
        Flux<ComfyWebSocketEvent> secondFlux = webSocketClient.openSession(clientId);

        assertNotNull(firstFlux);
        assertNotNull(secondFlux);
    }

    @Test
    void hasActiveSession_afterOpen_shouldReturnTrue() {
        UUID clientId = UUID.randomUUID();

        webSocketClient.openSession(clientId);

        assertTrue(webSocketClient.hasActiveSession(clientId));
    }

    @Test
    void hasActiveSession_beforeOpen_shouldReturnFalse() {
        UUID clientId = UUID.randomUUID();

        assertFalse(webSocketClient.hasActiveSession(clientId));
    }

    @Test
    void getSession_afterOpen_shouldReturnSession() {
        UUID clientId = UUID.randomUUID();

        webSocketClient.openSession(clientId);

        ComfyWebSocketSession session = webSocketClient.getSession(clientId);
        assertNotNull(session);
        assertEquals(clientId, session.getClientId());
    }

    @Test
    void getSession_beforeOpen_shouldReturnNull() {
        UUID clientId = UUID.randomUUID();

        ComfyWebSocketSession session = webSocketClient.getSession(clientId);

        assertNull(session);
    }

    @Test
    void closeSession_shouldRemoveSession() {
        UUID clientId = UUID.randomUUID();

        webSocketClient.openSession(clientId);
        assertTrue(webSocketClient.hasActiveSession(clientId));

        webSocketClient.closeSession(clientId);

        assertFalse(webSocketClient.hasActiveSession(clientId));
    }

    @Test
    void closeSession_nonExistentSession_shouldNotThrow() {
        UUID clientId = UUID.randomUUID();

        webSocketClient.closeSession(clientId);

        assertFalse(webSocketClient.hasActiveSession(clientId));
    }

    @Test
    void multipleSessions_shouldBeIndependent() {
        UUID clientId1 = UUID.randomUUID();
        UUID clientId2 = UUID.randomUUID();

        webSocketClient.openSession(clientId1);
        webSocketClient.openSession(clientId2);

        assertTrue(webSocketClient.hasActiveSession(clientId1));
        assertTrue(webSocketClient.hasActiveSession(clientId2));

        webSocketClient.closeSession(clientId1);

        assertFalse(webSocketClient.hasActiveSession(clientId1));
        assertTrue(webSocketClient.hasActiveSession(clientId2));
    }
}
