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
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    public static final String PUSH_NOTIFICATION_CONFIG_SET = "CreateTaskPushNotificationConfig";
    public static final String PUSH_NOTIFICATION_CONFIG_GET = "GetTaskPushNotificationConfig";
    public static final String PUSH_NOTIFICATION_CONFIG_LIST = "ListTaskPushNotificationConfigs";
    public static final String PUSH_NOTIFICATION_CONFIG_DELETE = "DeleteTaskPushNotificationConfig";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final ServerCallContextFactory serverCallContextFactory;

    public PushNotificationService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                                   ServerCallContextFactory serverCallContextFactory) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    public ResponseEntity<CreateTaskPushNotificationConfigResponse> setPushConfig(String agentId, CreateTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_SET,
                () -> new CreateTaskPushNotificationConfigResponse(request.getId(), requestHandler.onCreateTaskPushNotificationConfig(request.getParams(), context)),
                error -> new CreateTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(String agentId, GetTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_GET,
                () -> new GetTaskPushNotificationConfigResponse(request.getId(), requestHandler.onGetTaskPushNotificationConfig(request.getParams(), context)),
                error -> new GetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<ListTaskPushNotificationConfigsResponse> listPushConfigs(String agentId, ListTaskPushNotificationConfigsRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_LIST,
                () -> new ListTaskPushNotificationConfigsResponse(request.getId(), requestHandler.onListTaskPushNotificationConfigs(request.getParams(), context)),
                error -> new ListTaskPushNotificationConfigsResponse(request.getId(), error));
    }

    public ResponseEntity<DeleteTaskPushNotificationConfigResponse> deletePushConfig(String agentId, DeleteTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_DELETE,
                () -> {
                    requestHandler.onDeleteTaskPushNotificationConfig(request.getParams(), context);
                    return new DeleteTaskPushNotificationConfigResponse(request.getId());
                },
                error -> new DeleteTaskPushNotificationConfigResponse(request.getId(), error));
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
