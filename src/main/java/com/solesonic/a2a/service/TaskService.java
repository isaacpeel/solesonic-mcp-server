package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.a2aproject.sdk.jsonrpc.common.wrappers.*;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    public static final String MESSAGE_SEND = "SendMessage";
    public static final String TASKS_GET = "GetTask";
    public static final String TASKS_CANCEL = "CancelTask";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final ServerCallContextFactory serverCallContextFactory;
    private final A2ARpcExecutor a2aRpcExecutor;

    public TaskService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                       ServerCallContextFactory serverCallContextFactory,
                       A2ARpcExecutor a2aRpcExecutor) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.serverCallContextFactory = serverCallContextFactory;
        this.a2aRpcExecutor = a2aRpcExecutor;
    }

    public ResponseEntity<SendMessageResponse> send(String agentId, SendMessageRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), MESSAGE_SEND,
                () -> new SendMessageResponse(request.getId(), requestHandler.onMessageSend(request.getParams(), context)),
                error -> new SendMessageResponse(request.getId(), error));
    }

    public ResponseEntity<GetTaskResponse> getTask(String agentId, GetTaskRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), TASKS_GET,
                () -> new GetTaskResponse(request.getId(), requestHandler.onGetTask(request.getParams(), context)),
                error -> new GetTaskResponse(request.getId(), error));
    }

    public ResponseEntity<CancelTaskResponse> cancelTask(String agentId, CancelTaskRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), TASKS_CANCEL,
                () -> new CancelTaskResponse(request.getId(), requestHandler.onCancelTask(request.getParams(), context)),
                error -> new CancelTaskResponse(request.getId(), error));
    }
}
