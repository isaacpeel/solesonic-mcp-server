package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.a2aproject.sdk.jsonrpc.common.wrappers.*;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
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

    public static final String MESSAGE_SEND = "SendMessage";
    public static final String TASKS_GET = "GetTask";
    public static final String TASKS_CANCEL = "CancelTask";

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
            Function<A2AError, T> errorBody) {
        try {
            return jsonResponse(successBody.call());
        } catch (A2AError a2aError) {
            return jsonResponse(errorBody.apply(a2aError));
        } catch (IllegalArgumentException invalidParams) {
            return jsonResponse(errorBody.apply(
                    new InvalidParamsError("Invalid params: " + invalidParams.getMessage())));
        } catch (Exception unexpected) {
            log.error("Unexpected error handling {}: id={}", methodName, id, unexpected);
            return jsonResponse(errorBody.apply(new InternalError("Internal error")));
        }
    }

    private static <T> ResponseEntity<T> jsonResponse(T body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
