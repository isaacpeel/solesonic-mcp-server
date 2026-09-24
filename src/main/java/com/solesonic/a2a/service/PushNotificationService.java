package com.solesonic.a2a.service;

import com.solesonic.a2a.config.AgentRequestHandlerRegistry;
import com.solesonic.a2a.config.ServerCallContextFactory;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CreateTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.DeleteTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskPushNotificationConfigResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTaskPushNotificationConfigsResponse;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    public static final String PUSH_NOTIFICATION_CONFIG_SET = "CreateTaskPushNotificationConfig";
    public static final String PUSH_NOTIFICATION_CONFIG_GET = "GetTaskPushNotificationConfig";
    public static final String PUSH_NOTIFICATION_CONFIG_LIST = "ListTaskPushNotificationConfigs";
    public static final String PUSH_NOTIFICATION_CONFIG_DELETE = "DeleteTaskPushNotificationConfig";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final ServerCallContextFactory serverCallContextFactory;
    private final A2ARpcExecutor a2aRpcExecutor;

    public PushNotificationService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                                   ServerCallContextFactory serverCallContextFactory,
                                   A2ARpcExecutor a2aRpcExecutor) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.serverCallContextFactory = serverCallContextFactory;
        this.a2aRpcExecutor = a2aRpcExecutor;
    }

    public ResponseEntity<CreateTaskPushNotificationConfigResponse> setPushConfig(String agentId, CreateTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), PUSH_NOTIFICATION_CONFIG_SET,
                () -> new CreateTaskPushNotificationConfigResponse(request.getId(), requestHandler.onCreateTaskPushNotificationConfig(request.getParams(), context)),
                error -> new CreateTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(String agentId, GetTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), PUSH_NOTIFICATION_CONFIG_GET,
                () -> new GetTaskPushNotificationConfigResponse(request.getId(), requestHandler.onGetTaskPushNotificationConfig(request.getParams(), context)),
                error -> new GetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<ListTaskPushNotificationConfigsResponse> listPushConfigs(String agentId, ListTaskPushNotificationConfigsRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), PUSH_NOTIFICATION_CONFIG_LIST,
                () -> new ListTaskPushNotificationConfigsResponse(request.getId(), requestHandler.onListTaskPushNotificationConfigs(request.getParams(), context)),
                error -> new ListTaskPushNotificationConfigsResponse(request.getId(), error));
    }

    public ResponseEntity<DeleteTaskPushNotificationConfigResponse> deletePushConfig(String agentId, DeleteTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return a2aRpcExecutor.execute(request.getId(), PUSH_NOTIFICATION_CONFIG_DELETE,
                () -> {
                    requestHandler.onDeleteTaskPushNotificationConfig(request.getParams(), context);
                    return new DeleteTaskPushNotificationConfigResponse(request.getId());
                },
                error -> new DeleteTaskPushNotificationConfigResponse(request.getId(), error));
    }
}
