package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class A2ARedisConfiguration {

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
}
