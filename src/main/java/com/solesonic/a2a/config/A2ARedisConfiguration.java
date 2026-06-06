package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import io.a2a.server.config.DefaultValuesConfigProvider;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

@Configuration
public class A2ARedisConfiguration {

    public static final String A_2_A_AGENT_EXECUTOR = "a2a-agent-executor-";
    public static final String SPORTS_CHAT_MEMORY = "sportsChatMemory";


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
