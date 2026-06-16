package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisPushNotificationConfigStore;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.Executor;

@Configuration
public class A2ARedisConfiguration {

    public static final String A_2_A_AGENT_EXECUTOR = "a2a-agent-executor-";
    public static final String SPORTS_CHAT_MEMORY = "sportsChatMemory";

    @Bean
    public RedisTaskStore redisTaskStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisTaskStore(stringRedisTemplate, JsonUtil.OBJECT_MAPPER);
    }

    @Bean
    public RedisEventEnqueueHookFactory redisEventEnqueueHookFactory(StringRedisTemplate stringRedisTemplate) {
        return new RedisEventEnqueueHookFactory(stringRedisTemplate, JsonUtil.OBJECT_MAPPER);
    }

    @Bean
    public RedisPushNotificationConfigStore redisPushNotificationConfigStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisPushNotificationConfigStore(stringRedisTemplate, JsonUtil.OBJECT_MAPPER);
    }

    @Bean
    public MainEventBus mainEventBus() {
        return new MainEventBus();
    }

    @Bean
    public RedisQueueManager redisQueueManager(RedisTaskStore redisTaskStore,
                                               RedisEventEnqueueHookFactory redisEventEnqueueHookFactory,
                                               MainEventBus mainEventBus) {
        return RedisQueueManager.create(redisTaskStore, redisEventEnqueueHookFactory, mainEventBus);
    }

    @Bean
    public MainEventBusProcessor mainEventBusProcessor(MainEventBus mainEventBus,
                                                       TaskStore taskStore,
                                                       PushNotificationSender pushNotificationSender,
                                                       RedisQueueManager redisQueueManager) {
        return new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, redisQueueManager);
    }

    @Bean
    public DefaultValuesConfigProvider a2aConfigProvider() {
        return new DefaultValuesConfigProvider();
    }

    @Bean(SPORTS_CHAT_MEMORY)
    public ChatMemory sportsChatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public Executor a2aExecutor() {
        SimpleAsyncTaskExecutor simpleAsyncTaskExecutor = new SimpleAsyncTaskExecutor(A_2_A_AGENT_EXECUTOR);
        simpleAsyncTaskExecutor.setVirtualThreads(true);
        return simpleAsyncTaskExecutor;
    }
}
