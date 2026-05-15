package com.solesonic.a2a.service;

import tools.jackson.databind.json.JsonMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.DeleteTaskPushNotificationConfigRequest;
import io.a2a.spec.DeleteTaskPushNotificationConfigResponse;
import io.a2a.spec.EventKind;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskPushNotificationConfigResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.ListTaskPushNotificationConfigRequest;
import io.a2a.spec.ListTaskPushNotificationConfigResponse;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.SetTaskPushNotificationConfigResponse;
import io.a2a.spec.StreamingEventKind;
import io.a2a.spec.Task;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.TaskResubscriptionRequest;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.adapter.JdkFlowAdapter;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

@Service
public class A2AService {

    private static final Logger log = LoggerFactory.getLogger(A2AService.class);

    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final RequestHandler requestHandler;
    private final JsonMapper jsonMapper;
    private final ServerCallContextFactory serverCallContextFactory;

    public A2AService(RequestHandler requestHandler, JsonMapper jsonMapper, ServerCallContextFactory serverCallContextFactory) {
        this.requestHandler = requestHandler;
        this.jsonMapper = jsonMapper;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    // --- Non-streaming operations ---

    public ResponseEntity<Object> send(SendMessageRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "message/send",
                () -> {
                    EventKind result = requestHandler.onMessageSend(request.getParams(), context);
                    return new SendMessageResponse(request.getId(), result);
                },
                error -> new SendMessageResponse(request.getId(), error));
    }

    public ResponseEntity<Object> getTask(GetTaskRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "tasks/get",
                () -> {
                    Task task = requestHandler.onGetTask(request.getParams(), context);
                    return new GetTaskResponse(request.getId(), task);
                },
                error -> new GetTaskResponse(request.getId(), error));
    }

    public ResponseEntity<Object> cancelTask(CancelTaskRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "tasks/cancel",
                () -> {
                    Task task = requestHandler.onCancelTask(request.getParams(), context);
                    return new CancelTaskResponse(request.getId(), task);
                },
                error -> new CancelTaskResponse(request.getId(), error));
    }

    public ResponseEntity<Object> setPushConfig(SetTaskPushNotificationConfigRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "pushNotificationConfig/set",
                () -> {
                    TaskPushNotificationConfig stored = requestHandler.onSetTaskPushNotificationConfig(request.getParams(), context);
                    return new SetTaskPushNotificationConfigResponse(request.getId(), stored);
                },
                error -> new SetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<Object> getPushConfig(GetTaskPushNotificationConfigRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "pushNotificationConfig/get",
                () -> {
                    TaskPushNotificationConfig pushConfig = requestHandler.onGetTaskPushNotificationConfig(request.getParams(), context);
                    return new GetTaskPushNotificationConfigResponse(request.getId(), pushConfig);
                },
                error -> new GetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<Object> listPushConfigs(ListTaskPushNotificationConfigRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "pushNotificationConfig/list",
                () -> {
                    List<TaskPushNotificationConfig> pushConfigs = requestHandler.onListTaskPushNotificationConfig(request.getParams(), context);
                    return new ListTaskPushNotificationConfigResponse(request.getId(), pushConfigs);
                },
                error -> new ListTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<Object> deletePushConfig(DeleteTaskPushNotificationConfigRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), "pushNotificationConfig/delete",
                () -> {
                    requestHandler.onDeleteTaskPushNotificationConfig(request.getParams(), context);
                    return new DeleteTaskPushNotificationConfigResponse(request.getId());
                },
                error -> new DeleteTaskPushNotificationConfigResponse(request.getId(), error));
    }

    // --- Streaming operations ---

    public SseEmitter stream(SendStreamingMessageRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeStreamRpc(request.getId(), "message/stream",
                () -> requestHandler.onMessageSendStream(request.getParams(), context));
    }

    public SseEmitter resubscribe(TaskResubscriptionRequest request) {
        ServerCallContext context = serverCallContextFactory.create();
        return executeStreamRpc(request.getId(), "tasks/resubscribe",
                () -> requestHandler.onResubscribeToTask(request.getParams(), context));
    }

    // --- Execution helpers ---

    private ResponseEntity<Object> executeRpc(
            Object id,
            String methodName,
            Callable<Object> successBody,
            Function<JSONRPCError, Object> errorBody) {
        try {
            return jsonResponse(successBody.call());
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(errorBody.apply(jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(errorBody.apply(
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return jsonResponse(errorBody.apply(
                    new JSONRPCError(INTERNAL_ERROR, "Internal error", null)));
        }
    }

    private SseEmitter executeStreamRpc(
            Object id,
            String methodName,
            Callable<Flow.Publisher<StreamingEventKind>> publisherCallable) {
        try {
            return streamFromPublisher(id, publisherCallable.call());
        } catch (JSONRPCError jsonRpcError) {
            return sseError(id, jsonRpcError);
        } catch (IllegalArgumentException invalidParams) {
            return sseError(id, new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return sseError(id, new JSONRPCError(INTERNAL_ERROR, "Internal error", null));
        }
    }

    // --- SSE helpers ---

    private SseEmitter streamFromPublisher(Object id, Flow.Publisher<StreamingEventKind> publisher) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // EventConsumer's ZeroPublisher runs its polling loop synchronously on the subscribing
        // thread. Subscribing on a background thread lets Spring commit the SSE response headers
        // immediately, so each emitter.send() flushes to the client as events arrive rather than
        // buffering until the workflow completes.
        CompletableFuture.runAsync(() ->
            JdkFlowAdapter.flowPublisherToFlux(publisher)
                    .subscribe(
                        event -> {
                            try {
                                SendStreamingMessageResponse response = new SendStreamingMessageResponse(id, event);
                                emitter.send(SseEmitter.event()
                                        .data(jsonMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
                            } catch (Exception sendError) {
                                emitter.completeWithError(sendError);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                    )
        );

        return emitter;
    }

    public SseEmitter sseError(Object id, JSONRPCError error) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            SendStreamingMessageResponse errorResponse = new SendStreamingMessageResponse(id, error);
            emitter.send(SseEmitter.event()
                    .data(jsonMapper.writeValueAsString(errorResponse), MediaType.APPLICATION_JSON));
        } catch (Exception serializationError) {
            emitter.completeWithError(serializationError);
        }
        emitter.complete();
        return emitter;
    }

    // --- Utility ---

    private static ResponseEntity<Object> jsonResponse(Object body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
