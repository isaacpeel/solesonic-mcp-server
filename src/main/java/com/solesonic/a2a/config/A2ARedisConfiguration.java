package com.solesonic.a2a.config;

import com.solesonic.a2a.redis.RedisEventEnqueueHookFactory;
import com.solesonic.a2a.redis.RedisQueueManager;
import com.solesonic.a2a.redis.RedisTaskStore;
import io.a2a.server.config.DefaultValuesConfigProvider;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
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

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration("localhost", 6379);
        redisStandaloneConfiguration.setUsername("default");
        redisStandaloneConfiguration.setPassword("n123N!@#");
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(@Value("${spring.ai.chat.memory.redis.key-prefix}") String keyPrefix,
                                                               @Value("${spring.ai.chat.memory.redis.index-name}") String indexName) {

        JedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder()
                .user("default")
                .password("n123N!@#")
                .build();

        JedisPooled jedisClient = new JedisPooled(new HostAndPort("localhost", 6379), jedisClientConfig);

        return RedisChatMemoryRepository.builder()
                .jedisClient(jedisClient)
                .indexName(indexName)
                .keyPrefix(keyPrefix)
                .timeToLive(Duration.ofHours(24))
                .build();
    }

    @Bean(SPORTS_CHAT_MEMORY)
    public ChatMemory sportsChatMemory(RedisChatMemoryRepository chatMemoryRepository) {
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
