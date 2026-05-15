package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.config.DefaultValuesConfigProvider;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

@Configuration
public class A2ARedisConfiguration {

    public static final String A_2_A_AGENT_EXECUTOR = "a2a-agent-executor-";

    @Bean
    public RedisTaskStore redisTaskStore(StringRedisTemplate stringRedisTemplate, JsonMapper jsonMapper) {
        return new RedisTaskStore(stringRedisTemplate, jsonMapper);
    }

    @Bean
    public RedisEventEnqueueHookFactory redisEventEnqueueHookFactory(StringRedisTemplate stringRedisTemplate,
                                                                     JsonMapper jsonMapper) {
        return new RedisEventEnqueueHookFactory(stringRedisTemplate, jsonMapper);
    }

    @Bean
    public RedisQueueManager redisQueueManager(RedisTaskStore redisTaskStore,
                                               RedisEventEnqueueHookFactory redisEventEnqueueHookFactory) {
        return RedisQueueManager.create(redisTaskStore, redisEventEnqueueHookFactory);
    }

    @Bean
    public DefaultValuesConfigProvider a2aConfigProvider() {
        return new DefaultValuesConfigProvider();
    }

    @Bean
    public Executor a2aExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(A_2_A_AGENT_EXECUTOR);
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean
    public RequestHandler requestHandler(AgentExecutor agentExecutor,
                                         TaskStore taskStore,
                                         QueueManager queueManager,
                                         PushNotificationConfigStore pushNotificationConfigStore,
                                         PushNotificationSender pushNotificationSender,
                                         Executor a2aExecutor) {
        return DefaultRequestHandler.create(agentExecutor, taskStore, queueManager,
                pushNotificationConfigStore, pushNotificationSender, a2aExecutor);
    }
}
