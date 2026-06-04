package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
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
    void send_success_returns200WithBody() throws Exception {
        Task task = buildTask("task-1", TaskState.WORKING);
        when(requestHandler.onMessageSend(any(), any())).thenReturn(task);

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest("req-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNull();
        assertThat(response.getBody().getResult()).isNotNull();
    }

    @Test
    void send_jsonRpcError_returnsErrorBody() throws Exception {
        JSONRPCError jsonRpcError = new JSONRPCError(-32600, "Invalid request", null);
        when(requestHandler.onMessageSend(any(), any())).thenThrow(jsonRpcError);

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest("req-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getMessage()).isEqualTo("Invalid request");
    }

    @Test
    void send_illegalArgument_returnsInvalidParams() throws Exception {
        when(requestHandler.onMessageSend(any(), any())).thenThrow(new IllegalArgumentException("bad param"));

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest("req-1"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(-32602);
    }

    @Test
    void send_unexpectedException_returnsInternalError() throws Exception {
        when(requestHandler.onMessageSend(any(), any())).thenThrow(new RuntimeException("unexpected"));

        ResponseEntity<SendMessageResponse> response = service.send("agent-id", buildSendMessageRequest("req-1"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(-32603);
    }

    @Test
    void getTask_success_returns200WithBody() throws Exception {
        Task task = buildTask("task-1", TaskState.WORKING);
        when(requestHandler.onGetTask(any(), any())).thenReturn(task);

        GetTaskRequest request = new GetTaskRequest(null, "req-2", "tasks/get", new TaskQueryParams("task-1"));
        ResponseEntity<GetTaskResponse> response = service.getTask("agent-id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNull();
    }

    @Test
    void cancelTask_success_returns200WithBody() throws Exception {
        Task task = buildTask("task-1", TaskState.CANCELED);
        when(requestHandler.onCancelTask(any(), any())).thenReturn(task);

        CancelTaskRequest request = new CancelTaskRequest(null, "req-3", "tasks/cancel", new TaskIdParams("task-1"));
        ResponseEntity<CancelTaskResponse> response = service.cancelTask("agent-id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNull();
    }

    private Task buildTask(String taskId, TaskState taskState) {
        return new Task.Builder()
                .id(taskId)
                .contextId("ctx-" + taskId)
                .status(new TaskStatus(taskState))
                .build();
    }

    private SendMessageRequest buildSendMessageRequest(String requestId) {
        Message message = new Message.Builder()
                .role(Message.Role.USER)
                .parts(new TextPart("test message"))
                .build();
        MessageSendParams params = new MessageSendParams.Builder()
                .message(message)
                .build();
        return new SendMessageRequest("2.0", requestId, "message/send", params);
    }
}
