package com.solesonic.a2a.config;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Component
public class AgentRequestHandlerRegistry {

    private final Map<String, RequestHandler> handlersByAgentId;

    public AgentRequestHandlerRegistry(
            Map<String, AgentExecutor> agentExecutors,
            TaskStore taskStore,
            QueueManager queueManager,
            PushNotificationConfigStore pushNotificationConfigStore,
            PushNotificationSender pushNotificationSender,
            @Qualifier("a2aExecutor") Executor a2aExecutor) {

        this.handlersByAgentId = agentExecutors.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> DefaultRequestHandler.create(
                                entry.getValue(),
                                taskStore,
                                queueManager,
                                pushNotificationConfigStore,
                                pushNotificationSender,
                                a2aExecutor)));
    }

    public RequestHandler getHandler(String agentId) {
        RequestHandler handler = handlersByAgentId.get(agentId);

        if (handler == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }

        return handler;
    }
}
