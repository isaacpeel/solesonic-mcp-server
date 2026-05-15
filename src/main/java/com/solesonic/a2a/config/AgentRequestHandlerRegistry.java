package com.solesonic.a2a.config;

import io.a2a.server.requesthandlers.RequestHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class AgentRequestHandlerRegistry {

    private final Map<String, RequestHandler> handlersByAgentId;

    public AgentRequestHandlerRegistry(Map<String, RequestHandler> handlersByAgentId) {
        this.handlersByAgentId = handlersByAgentId;
    }

    public RequestHandler getHandler(String agentId) {
        RequestHandler handler = handlersByAgentId.get(agentId);

        if (handler == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId);
        }

        return handler;
    }
}
