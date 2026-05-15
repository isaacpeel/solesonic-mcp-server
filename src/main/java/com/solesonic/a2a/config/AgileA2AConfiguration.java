package com.solesonic.a2a.config;

import com.solesonic.a2a.agent.AgileAgentExecutor;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
public class AgileA2AConfiguration {

    @Bean(name = "agile-query-agent")
    public RequestHandler agileRequestHandler(AgileAgentExecutor agileAgentExecutor,
                                              TaskStore taskStore,
                                              QueueManager queueManager,
                                              PushNotificationConfigStore pushNotificationConfigStore,
                                              PushNotificationSender pushNotificationSender,
                                              Executor a2aExecutor) {
        return DefaultRequestHandler.create(
                agileAgentExecutor,
                taskStore,
                queueManager,
                pushNotificationConfigStore,
                pushNotificationSender,
                a2aExecutor);
    }
}
