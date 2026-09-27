package com.solesonic.a2a.api;

import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private StreamingA2AService streamingA2AService;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(taskService, streamingA2AService);
    }

    @Test
    void handleUnexpectedError_responseStatusException_rethrowsItUnchanged() {
        ResponseStatusException responseStatusException = new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: unknown-agent");

        assertThatThrownBy(() -> controller.handleUnexpectedError(responseStatusException))
                .isSameAs(responseStatusException);
    }

    @Test
    void handleUnexpectedError_genericRuntimeException_returnsInternalServerError() {
        RuntimeException runtimeException = new RuntimeException("boom");

        ResponseEntity<Void> response = controller.handleUnexpectedError(runtimeException);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }
}