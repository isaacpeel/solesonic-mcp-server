package com.solesonic.a2a.config;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Component
public class AgentRequestHandlerRegistry {

    public static final String A2A_EXECUTOR = "a2aExecutor";
    private final Map<String, RequestHandler> handlersByAgentId;

    public AgentRequestHandlerRegistry(
            Map<String, AgentExecutor> agentExecutors,
            TaskStore taskStore,
            QueueManager queueManager,
            PushNotificationConfigStore pushNotificationConfigStore,
            MainEventBusProcessor mainEventBusProcessor,
            @Qualifier(A2A_EXECUTOR) Executor a2aExecutor) {

        this.handlersByAgentId = agentExecutors.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> DefaultRequestHandler.create(
                                entry.getValue(),
                                taskStore,
                                queueManager,
                                pushNotificationConfigStore,
                                mainEventBusProcessor,
                                a2aExecutor,
                                a2aExecutor)));
    }

    public RequestHandler getHandler(String agentId) {
        RequestHandler requestHandler = handlersByAgentId.get(agentId);

        if (requestHandler == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }

        return requestHandler;
    }
}
