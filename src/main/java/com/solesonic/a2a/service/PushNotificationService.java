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
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    public static final String PUSH_NOTIFICATION_CONFIG_SET = "pushNotificationConfig/set";
    public static final String PUSH_NOTIFICATION_CONFIG_GET = "pushNotificationConfig/get";
    public static final String PUSH_NOTIFICATION_CONFIG_LIST = "pushNotificationConfig/list";
    public static final String PUSH_NOTIFICATION_CONFIG_DELETE = "pushNotificationConfig/delete";

    private final AgentRequestHandlerRegistry agentRequestHandlerRegistry;
    private final ServerCallContextFactory serverCallContextFactory;

    public PushNotificationService(AgentRequestHandlerRegistry agentRequestHandlerRegistry,
                                   ServerCallContextFactory serverCallContextFactory) {
        this.agentRequestHandlerRegistry = agentRequestHandlerRegistry;
        this.serverCallContextFactory = serverCallContextFactory;
    }

    public ResponseEntity<SetTaskPushNotificationConfigResponse> setPushConfig(String agentId, SetTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_SET,
                () -> new SetTaskPushNotificationConfigResponse(request.getId(), requestHandler.onSetTaskPushNotificationConfig(request.getParams(), context)),
                error -> new SetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<GetTaskPushNotificationConfigResponse> getPushConfig(String agentId, GetTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_GET,
                () -> new GetTaskPushNotificationConfigResponse(request.getId(), requestHandler.onGetTaskPushNotificationConfig(request.getParams(), context)),
                error -> new GetTaskPushNotificationConfigResponse(request.getId(), error));
    }

    public ResponseEntity<ListTaskPushNotificationConfigResponse> listPushConfigs(String agentId, ListTaskPushNotificationConfigRequest request) {
        RequestHandler requestHandler = agentRequestHandlerRegistry.getHandler(agentId);
        ServerCallContext context = serverCallContextFactory.create();
        return executeRpc(request.getId(), PUSH_NOTIFICATION_CONFIG_LIST,
                () -> new ListTaskPushNotificationConfigResponse(request.getId(), requestHandler.onListTaskPushNotificationConfig(request.getParams(), context)),
                error -> new ListTaskPushNotificationConfigResponse(request.getId(), error));
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
