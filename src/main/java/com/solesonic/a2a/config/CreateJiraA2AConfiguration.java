package com.solesonic.a2a.config;

import com.solesonic.a2a.agent.CreateJiraAgentExecutor;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

@Configuration
public class CreateJiraA2AConfiguration {

    @Bean(name = "createJiraRequestHandler")
    public RequestHandler createJiraRequestHandler(CreateJiraAgentExecutor createJiraAgentExecutor,
                                                   TaskStore taskStore,
                                                   QueueManager queueManager,
                                                   PushNotificationConfigStore pushNotificationConfigStore,
                                                   PushNotificationSender pushNotificationSender,
                                                   Executor a2aExecutor) {
        return DefaultRequestHandler.create(createJiraAgentExecutor, taskStore, queueManager,
                pushNotificationConfigStore, pushNotificationSender, a2aExecutor);
    }

    @Bean(name = "createJiraTaskService")
    public TaskService createJiraTaskService(RequestHandler createJiraRequestHandler,
                                             ServerCallContextFactory serverCallContextFactory) {
        return new TaskService(createJiraRequestHandler, serverCallContextFactory);
    }

    @Bean(name = "createJiraStreamingA2AService")
    public StreamingA2AService createJiraStreamingA2AService(RequestHandler createJiraRequestHandler,
                                                             JsonMapper jsonMapper,
                                                             ServerCallContextFactory serverCallContextFactory) {
        return new StreamingA2AService(createJiraRequestHandler, jsonMapper, serverCallContextFactory);
    }
}
