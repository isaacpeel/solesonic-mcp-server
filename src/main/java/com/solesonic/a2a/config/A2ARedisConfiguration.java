package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import io.a2a.server.config.DefaultValuesConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.Executor;

@Configuration
public class A2ARedisConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(A2ARedisConfiguration.class);

    public static final String A_2_A_AGENT_EXECUTOR = "a2a-agent-executor-";

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private final RedisConnectionFactory redisConnectionFactory;

    public A2ARedisConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRedisConnectionStatus() {
        logger.info("Redis configured: host={} port={}", redisHost, redisPort);
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            logger.info("Redis connection OK ({})", pong);
        } catch (Exception exception) {
            logger.error("Redis connection FAILED: host={} port={} error={}", redisHost, redisPort, exception.getMessage());
        }
    }

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
}
