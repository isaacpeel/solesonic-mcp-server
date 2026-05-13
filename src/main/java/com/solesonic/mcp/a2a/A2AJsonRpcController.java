package com.solesonic.mcp.a2a;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.DeleteTaskPushNotificationConfigParams;
import io.a2a.spec.DeleteTaskPushNotificationConfigRequest;
import io.a2a.spec.DeleteTaskPushNotificationConfigResponse;
import io.a2a.spec.EventKind;
import io.a2a.spec.GetTaskPushNotificationConfigParams;
import io.a2a.spec.GetTaskPushNotificationConfigRequest;
import io.a2a.spec.GetTaskPushNotificationConfigResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.ListTaskPushNotificationConfigParams;
import io.a2a.spec.ListTaskPushNotificationConfigRequest;
import io.a2a.spec.ListTaskPushNotificationConfigResponse;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.SetTaskPushNotificationConfigRequest;
import io.a2a.spec.SetTaskPushNotificationConfigResponse;
import io.a2a.spec.StreamingEventKind;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskResubscriptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.adapter.JdkFlowAdapter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

@RestController
public class A2AJsonRpcController {

    private static final Logger log = LoggerFactory.getLogger(A2AJsonRpcController.class);

    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final RequestHandler requestHandler;
    private final JsonMapper jsonMapper;

    public A2AJsonRpcController(RequestHandler requestHandler, JsonMapper jsonMapper) {
        this.requestHandler = requestHandler;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object dispatch(@RequestBody JsonNode body) {
        String method = body.path("method").asText();
        Object id = readId(body.path("id"));
        JsonNode params = body.path("params");

        log.debug("A2A JSON-RPC dispatch: method={}, id={}", method, id);

        ServerCallContext context = serverCallContext();

        return switch (method) {
            case SendMessageRequest.METHOD -> handleSend(id, params, context);
            case SendStreamingMessageRequest.METHOD -> handleStream(id, params, context);
            case GetTaskRequest.METHOD -> handleGetTask(id, params, context);
            case CancelTaskRequest.METHOD -> handleCancelTask(id, params, context);
            case TaskResubscriptionRequest.METHOD -> handleResubscribe(id, params, context);
            case SetTaskPushNotificationConfigRequest.METHOD -> handlePushSet(id, params, context);
            case GetTaskPushNotificationConfigRequest.METHOD -> handlePushGet(id, params, context);
            case ListTaskPushNotificationConfigRequest.METHOD -> handlePushList(id, params, context);
            case DeleteTaskPushNotificationConfigRequest.METHOD -> handlePushDelete(id, params, context);
            default -> jsonResponse(new JSONRPCErrorResponse(id,
                    new JSONRPCError(METHOD_NOT_FOUND, "Method not found: " + method, null)));
        };
    }

    private ResponseEntity<Object> handleSend(Object id, JsonNode params, ServerCallContext context) {
        try {
            MessageSendParams sendParams = jsonMapper.treeToValue(params, MessageSendParams.class);
            EventKind result = requestHandler.onMessageSend(sendParams, context);
            return jsonResponse(new SendMessageResponse(id, result));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new SendMessageResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new SendMessageResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling message/send: id={}", id, unexpected);
            return jsonResponse(new SendMessageResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private Object handleStream(Object id, JsonNode params, ServerCallContext context) {
        try {
            MessageSendParams sendParams = jsonMapper.treeToValue(params, MessageSendParams.class);
            Flow.Publisher<StreamingEventKind> publisher =
                    requestHandler.onMessageSendStream(sendParams, context);
            return streamFromPublisher(id, publisher);
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new SendStreamingMessageResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new SendStreamingMessageResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling message/stream: id={}", id, unexpected);
            return jsonResponse(new SendStreamingMessageResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private Object handleResubscribe(Object id, JsonNode params, ServerCallContext context) {
        try {
            TaskIdParams taskIdParams = jsonMapper.treeToValue(params, TaskIdParams.class);
            Flow.Publisher<StreamingEventKind> publisher =
                    requestHandler.onResubscribeToTask(taskIdParams, context);
            return streamFromPublisher(id, publisher);
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new SendStreamingMessageResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new SendStreamingMessageResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling tasks/resubscribe: id={}", id, unexpected);
            return jsonResponse(new SendStreamingMessageResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handleGetTask(Object id, JsonNode params, ServerCallContext context) {
        try {
            TaskQueryParams queryParams = jsonMapper.treeToValue(params, TaskQueryParams.class);
            Task task = requestHandler.onGetTask(queryParams, context);
            return jsonResponse(new GetTaskResponse(id, task));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new GetTaskResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new GetTaskResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling tasks/get: id={}", id, unexpected);
            return jsonResponse(new GetTaskResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handleCancelTask(Object id, JsonNode params, ServerCallContext context) {
        try {
            TaskIdParams taskIdParams = jsonMapper.treeToValue(params, TaskIdParams.class);
            Task task = requestHandler.onCancelTask(taskIdParams, context);
            return jsonResponse(new CancelTaskResponse(id, task));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new CancelTaskResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new CancelTaskResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling tasks/cancel: id={}", id, unexpected);
            return jsonResponse(new CancelTaskResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handlePushSet(Object id, JsonNode params, ServerCallContext context) {
        try {
            TaskPushNotificationConfig pushConfig = jsonMapper.treeToValue(params, TaskPushNotificationConfig.class);
            TaskPushNotificationConfig stored =
                    requestHandler.onSetTaskPushNotificationConfig(pushConfig, context);
            return jsonResponse(new SetTaskPushNotificationConfigResponse(id, stored));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new SetTaskPushNotificationConfigResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new SetTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling pushNotificationConfig/set: id={}", id, unexpected);
            return jsonResponse(new SetTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handlePushGet(Object id, JsonNode params, ServerCallContext context) {
        try {
            GetTaskPushNotificationConfigParams getParams =
                    jsonMapper.treeToValue(params, GetTaskPushNotificationConfigParams.class);
            TaskPushNotificationConfig pushConfig =
                    requestHandler.onGetTaskPushNotificationConfig(getParams, context);
            return jsonResponse(new GetTaskPushNotificationConfigResponse(id, pushConfig));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new GetTaskPushNotificationConfigResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new GetTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling pushNotificationConfig/get: id={}", id, unexpected);
            return jsonResponse(new GetTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handlePushList(Object id, JsonNode params, ServerCallContext context) {
        try {
            ListTaskPushNotificationConfigParams listParams =
                    jsonMapper.treeToValue(params, ListTaskPushNotificationConfigParams.class);
            List<TaskPushNotificationConfig> pushConfigs =
                    requestHandler.onListTaskPushNotificationConfig(listParams, context);
            return jsonResponse(new ListTaskPushNotificationConfigResponse(id, pushConfigs));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new ListTaskPushNotificationConfigResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new ListTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling pushNotificationConfig/list: id={}", id, unexpected);
            return jsonResponse(new ListTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private ResponseEntity<Object> handlePushDelete(Object id, JsonNode params, ServerCallContext context) {
        try {
            DeleteTaskPushNotificationConfigParams deleteParams =
                    jsonMapper.treeToValue(params, DeleteTaskPushNotificationConfigParams.class);
            requestHandler.onDeleteTaskPushNotificationConfig(deleteParams, context);
            return jsonResponse(new DeleteTaskPushNotificationConfigResponse(id));
        } catch (JSONRPCError jsonRpcError) {
            return jsonResponse(new DeleteTaskPushNotificationConfigResponse(id, jsonRpcError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(new DeleteTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INVALID_PARAMS, "Invalid params: " + invalidParams.getMessage(), null)));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling pushNotificationConfig/delete: id={}", id, unexpected);
            return jsonResponse(new DeleteTaskPushNotificationConfigResponse(id,
                    new JSONRPCError(INTERNAL_ERROR, "Internal error: " + unexpected.getMessage(), null)));
        }
    }

    private SseEmitter streamFromPublisher(Object id, Flow.Publisher<StreamingEventKind> publisher) {
        SseEmitter emitter = new SseEmitter(300_000L);

        // The EventConsumer's ZeroPublisher runs its polling loop synchronously on the subscribing
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

    private static ResponseEntity<Object> jsonResponse(Object body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static ServerCallContext serverCallContext() {
        return new ServerCallContext(null, Map.of(), Set.of());
    }

    private static Object readId(JsonNode idNode) {
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            return null;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.asLong();
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        return idNode.asText();
    }
}
