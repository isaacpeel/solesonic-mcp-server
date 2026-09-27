package com.solesonic.agent.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleaningCheckpointSaverTest {

    private static final String THREAD_NAME = "agile:chat-1";
    private static final String INTERNAL_THREAD_ID = "internal-7";

    private final McpCheckpointKeyNamingStrategy keyNamingStrategy = new McpCheckpointKeyNamingStrategy();
    private final RunnableConfig config = RunnableConfig.builder().threadId(THREAD_NAME).build();

    private RedisSaver redisSaver;
    private RBucket<String> threadNameBucket;
    private CleaningCheckpointSaver saver;

    @BeforeEach
    void setUp() {
        redisSaver = mock(RedisSaver.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        threadNameBucket = mock();

        when(redisSaver.threadId(config)).thenReturn(THREAD_NAME);
        when(redissonClient.<String>getBucket(eq(keyNamingStrategy.threadNameKey(THREAD_NAME)), any(Codec.class)))
                .thenReturn(threadNameBucket);

        saver = new CleaningCheckpointSaver(redisSaver, redissonClient, keyNamingStrategy);
    }

    @Test
    void release_releasesThenDeletesTheThreadsKeys() throws Exception {
        BaseCheckpointSaver.Tag tag = new BaseCheckpointSaver.Tag(THREAD_NAME, List.of());
        when(threadNameBucket.get()).thenReturn(INTERNAL_THREAD_ID);
        when(redisSaver.release(config)).thenReturn(tag);

        assertThat(saver.release(config)).isSameAs(tag);

        InOrder releaseThenCleanup = inOrder(redisSaver);
        releaseThenCleanup.verify(redisSaver).release(config);
        releaseThenCleanup.verify(redisSaver).cleanupThread(INTERNAL_THREAD_ID);
    }

    @Test
    void releaseOnError_alsoDeletesTheThreadsKeys() throws Exception {
        IllegalStateException failure = new IllegalStateException("node failed");
        when(threadNameBucket.get()).thenReturn(INTERNAL_THREAD_ID);

        saver.releaseOnError(config, failure);

        verify(redisSaver).releaseOnError(config, failure);
        verify(redisSaver).cleanupThread(INTERNAL_THREAD_ID);
    }

    @Test
    void release_ofAThreadThatNeverCheckpointed_skipsCleanup() throws Exception {
        when(threadNameBucket.get()).thenReturn(null);

        saver.release(config);

        verify(redisSaver).release(config);
        verify(redisSaver, never()).cleanupThread(anyString());
    }

    @Test
    void reads_delegateToTheRedisSaver() {
        saver.get(config);
        saver.list(config);

        verify(redisSaver).get(config);
        verify(redisSaver).list(config);
    }
}
