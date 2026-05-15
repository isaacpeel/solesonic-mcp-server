package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.function.Function;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    public static final String MESSAGE_SEND = "message/send";
    public static final String TASKS_GET = "tasks/get";
    public static final String TASKS_CANCEL = "tasks/cancel";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final ServerCallContextFactory serverCallContextFactory;

    public TaskService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                       ServerCallContextFactory serverCallContextFactory) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    public ResponseEntity<SendMessageResponse> send(String agentId, SendMessageRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), MESSAGE_SEND,
                () -> new SendMessageResponse(request.getId(), requestHandler.onMessageSend(request.getParams(), context)),
                error -> new SendMessageResponse(request.getId(), error));
    }

    public ResponseEntity<GetTaskResponse> getTask(String agentId, GetTaskRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), TASKS_GET,
                () -> new GetTaskResponse(request.getId(), requestHandler.onGetTask(request.getParams(), context)),
                error -> new GetTaskResponse(request.getId(), error));
    }

    public ResponseEntity<CancelTaskResponse> cancelTask(String agentId, CancelTaskRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), TASKS_CANCEL,
                () -> new CancelTaskResponse(request.getId(), requestHandler.onCancelTask(request.getParams(), context)),
                error -> new CancelTaskResponse(request.getId(), error));
    }

    private <T> ResponseEntity<T> executeRpc(
            Object id,
            String methodName,
            Callable<T> successBody,
            Function<JSONRPCError, T> errorBody) {
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

    private static <T> ResponseEntity<T> jsonResponse(T body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
