package com.solesonic.a2a.config;

import com.solesonic.a2a.agent.SportsAgentExecutor;
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
public class SportsA2AConfiguration {

    @Bean(name = "nba-sports-ball")
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
}
