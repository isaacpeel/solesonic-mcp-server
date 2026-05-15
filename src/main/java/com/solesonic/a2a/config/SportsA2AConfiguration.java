package com.solesonic.a2a.config;

import com.solesonic.a2a.agent.SportsAgentExecutor;
import com.solesonic.a2a.service.StreamingA2AService;
import com.solesonic.a2a.service.TaskService;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

@Configuration
public class SportsA2AConfiguration {

    @Bean(name = "sportsRequestHandler")
    @Primary
    public RequestHandler sportsRequestHandler(SportsAgentExecutor sportsAgentExecutor,
                                               TaskStore taskStore,
                                               QueueManager queueManager,
                                               PushNotificationConfigStore pushNotificationConfigStore,
                                               PushNotificationSender pushNotificationSender,
                                               Executor a2aExecutor) {
        return DefaultRequestHandler.create(sportsAgentExecutor,
                taskStore,
                queueManager,
                pushNotificationConfigStore,
                pushNotificationSender,
                a2aExecutor);
    }

    @Bean(name = "sportsTaskService")
    public TaskService sportsTaskService(RequestHandler sportsRequestHandler,
                                         ServerCallContextFactory serverCallContextFactory) {
        return new TaskService(sportsRequestHandler, serverCallContextFactory);
    }

    @Bean(name = "sportsStreamingA2AService")
    public StreamingA2AService sportsStreamingA2AService(RequestHandler sportsRequestHandler,
                                                         JsonMapper jsonMapper,
                                                         ServerCallContextFactory serverCallContextFactory) {
        return new StreamingA2AService(sportsRequestHandler, jsonMapper, serverCallContextFactory);
    }
}
