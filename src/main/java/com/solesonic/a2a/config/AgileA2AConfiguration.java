package com.solesonic.a2a.config;

import com.solesonic.a2a.agent.AgileAgentExecutor;
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
public class AgileA2AConfiguration {

    public static final String AGILE_REQUEST_HANDLER = "agileRequestHandler";
    public static final String AGILE_TASK_SERVICE = "agileTaskService";
    public static final String AGILE_STREAMING_A_2_A_SERVICE = "agileStreamingA2AService";

    @Bean(name = AGILE_REQUEST_HANDLER)
    public RequestHandler agileRequestHandler(AgileAgentExecutor agileAgentExecutor,
                                              TaskStore taskStore,
                                              QueueManager queueManager,
                                              PushNotificationConfigStore pushNotificationConfigStore,
                                              PushNotificationSender pushNotificationSender,
                                              Executor a2aExecutor) {
        return DefaultRequestHandler.create(
                agileAgentExecutor,
                taskStore, queueManager,
                pushNotificationConfigStore,
                pushNotificationSender,
                a2aExecutor);
    }

    @Bean(name = AGILE_TASK_SERVICE)
    public TaskService agileTaskService(RequestHandler agileRequestHandler,
                                        ServerCallContextFactory serverCallContextFactory) {
        return new TaskService(agileRequestHandler, serverCallContextFactory);
    }

    @Bean(name = AGILE_STREAMING_A_2_A_SERVICE)
    public StreamingA2AService agileStreamingA2AService(RequestHandler agileRequestHandler,
                                                        JsonMapper jsonMapper,
                                                        ServerCallContextFactory serverCallContextFactory) {
        return new StreamingA2AService(agileRequestHandler, jsonMapper, serverCallContextFactory);
    }
}
