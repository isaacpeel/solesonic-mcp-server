package com.solesonic.agent.checkpoint;

import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.KeyNamingStrategy;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The single checkpoint pattern every graph uses: one Redis-backed saver, one {@link CompileConfig}.
 * <p>
 * To checkpoint a new graph: compile it with {@code graphCompileConfig}, give it a {@code GRAPH_NAME}
 * constant, and start its runs through {@link GraphRunner} with a {@link GraphThread} config.
 * <p>
 * Redisson is a second Redis client alongside Spring Data Redis (LangGraph4j's saver is built on
 * it), pointed at the same {@code spring.data.redis.*} server. It is created lazily so nothing
 * connects to Redis until a graph first checkpoints.
 */
@Configuration
public class CheckpointConfig {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient checkpointRedissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                                   @Value("${spring.data.redis.port:6379}") int port,
                                                   @Value("${spring.data.redis.username:}") String username,
                                                   @Value("${spring.data.redis.password:}") String password,
                                                   @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled) {
        String scheme = sslEnabled ? "rediss://" : "redis://";

        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer().setAddress(scheme + host + ":" + port);

        if (StringUtils.isNotBlank(username)) {
            singleServerConfig.setUsername(username);
        }

        if (StringUtils.isNotBlank(password)) {
            singleServerConfig.setPassword(password);
        }

        return Redisson.create(config);
    }

    @Bean
    public BaseCheckpointSaver graphCheckpointSaver(@Lazy RedissonClient checkpointRedissonClient,
                                                    JsonMapper jsonMapper,
                                                    @Value("${solesonic.checkpoint.ttl:24h}") Duration checkpointTimeToLive) {
        KeyNamingStrategy keyNamingStrategy = new McpCheckpointKeyNamingStrategy();

        RedisSaver redisSaver = RedisSaver.builder()
                .redissonClient(checkpointRedissonClient)
                .keyNamingStrategy(keyNamingStrategy)
                .stateSerializer(new JsonMapperStateSerializer<>(AgentState::new, jsonMapper))
                .ttl(checkpointTimeToLive.toMillis(), TimeUnit.MILLISECONDS)
                .build();

        return new CleaningCheckpointSaver(redisSaver, checkpointRedissonClient, keyNamingStrategy);
    }

    @Bean
    public CompileConfig graphCompileConfig(BaseCheckpointSaver graphCheckpointSaver) {
        return CompileConfig.builder()
                .checkpointSaver(graphCheckpointSaver)
                .releaseThread(true)
                .build();
    }
}
