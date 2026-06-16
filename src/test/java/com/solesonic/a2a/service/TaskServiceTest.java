package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.a2aproject.sdk.jsonrpc.common.wrappers.*;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private AgentRequestHandlerRegistry agentRequestHandlerRegistry;

    @Mock
    private ServerCallContextFactory serverCallContextFactory;

    @Mock
    private RequestHandler requestHandler;

    @Mock
    private ServerCallContext serverCallContext;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(agentRequestHandlerRegistry, serverCallContextFactory);
        when(agentRequestHandlerRegistry.getHandler("agent-id")).thenReturn(requestHandler);
        when(serverCallContextFactory.create()).thenReturn(serverCallContext);
    }

    @Test
    void send_success_returns200WithBody() {
        Task task = buildTask(TaskState.TASK_STATE_WORKING);
        when(requestHandler.onMessageSend(any(), any())).thenReturn(task);

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNull();
        assertThat(response.getBody().getResult()).isNotNull();
    }

    @Test
    void send_jsonRpcError_returnsErrorBody() {
        when(requestHandler.onMessageSend(any(), any())).thenThrow(new InvalidRequestError("Invalid request"));

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Invalid request");
    }

    @Test
    void send_illegalArgument_returnsInvalidParams() {
        when(requestHandler.onMessageSend(any(), any())).thenThrow(new IllegalArgumentException("bad param"));

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest());

        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(-32602);
    }

    @Test
    void send_unexpectedException_returnsInternalError() {
        when(requestHandler.onMessageSend(any(), any())).thenThrow(new RuntimeException("unexpected"));

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest());

        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(-32603);
    }

    @Test
    void getTask_success_returns200WithBody() {
        Task task = buildTask(TaskState.TASK_STATE_WORKING);
        when(requestHandler.onGetTask(any(), any())).thenReturn(task);

        GetTaskRequest request = new GetTaskRequest(null, "req-2", new TaskQueryParams("task-1"));
        ResponseEntity<GetTaskResponse> response = service.getTask("agent-id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNull();
    }

    @Test
    void cancelTask_success_returns200WithBody() {
        Task task = buildTask(TaskState.TASK_STATE_CANCELED);
        when(requestHandler.onCancelTask(any(), any())).thenReturn(task);

        CancelTaskRequest request = new CancelTaskRequest(null, "req-3", new CancelTaskParams("task-1"));
        ResponseEntity<CancelTaskResponse> response = service.cancelTask("agent-id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assert response.getBody() != null;
        assertThat(response.getBody().getError()).isNull();
    }

    private Task buildTask(TaskState taskState) {
        return Task.builder()
                .id("task-1")
                .contextId("ctx-" + "task-1")
                .status(new TaskStatus(taskState))
                .build();
    }

    private SendMessageRequest buildSendMessageRequest() {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart("test message"))
                .build();
        MessageSendParams params = MessageSendParams.builder()
                .message(message)
                .build();
        return new SendMessageRequest("2.0", "req-1", params);
    }
}
