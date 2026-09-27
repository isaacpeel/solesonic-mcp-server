package com.solesonic.agent.checkpoint;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.KeyNamingStrategy;
import org.bsc.langgraph4j.checkpoint.RedisSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.jspecify.annotations.Nullable;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The checkpoint saver every graph compiles with: a {@link RedisSaver} whose release also deletes
 * the released thread's data.
 * <p>
 * LangGraph4j releases a graph's thread when the run reaches its end ({@code CompileConfig}'s
 * {@code releaseThread} defaults to true) or fails, but {@code RedisSaver.release} only flags the
 * thread released and drops its name lookup — the checkpoint hashes stay until TTL expiry. This
 * wrapper follows every release with {@link RedisSaver#cleanupThread(String)}, so finished runs leave
 * nothing behind and a later run under the same thread name starts fresh. Runs paused on an
 * interrupt are not released, so their checkpoints survive for resumption.
 */
public class CleaningCheckpointSaver implements BaseCheckpointSaver {

    private final RedisSaver redisSaver;
    private final RedissonClient redissonClient;
    private final KeyNamingStrategy keyNamingStrategy;

    public CleaningCheckpointSaver(RedisSaver redisSaver, RedissonClient redissonClient, KeyNamingStrategy keyNamingStrategy) {
        this.redisSaver = redisSaver;
        this.redissonClient = redissonClient;
        this.keyNamingStrategy = keyNamingStrategy;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return redisSaver.list(config);
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        return redisSaver.get(config);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        return redisSaver.put(config, checkpoint);
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        Optional<String> internalThreadId = activeInternalThreadId(config);
        Tag tag = redisSaver.release(config);
        internalThreadId.ifPresent(redisSaver::cleanupThread);
        return tag;
    }

    @Override
    public Tag release(RunnableConfig config, @Nullable String message) throws Exception {
        Optional<String> internalThreadId = activeInternalThreadId(config);
        Tag tag = redisSaver.release(config, message);
        internalThreadId.ifPresent(redisSaver::cleanupThread);
        return tag;
    }

    @Override
    public Tag releaseOnError(RunnableConfig config, Throwable exception) throws Exception {
        Optional<String> internalThreadId = activeInternalThreadId(config);
        Tag tag = redisSaver.releaseOnError(config, exception);
        internalThreadId.ifPresent(redisSaver::cleanupThread);
        return tag;
    }

    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> interruptionMetadata) {
        return redisSaver.registerInterruption(config, interruptionMetadata);
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, @Nullable Integer version) throws Exception {
        return redisSaver.tag(config, version);
    }

    @Override
    public void putSubGraphSaver(RunnableConfig parentConfig, RunnableConfig subGraphConfig, BaseCheckpointSaver subGraphSaver) {
        redisSaver.putSubGraphSaver(parentConfig, subGraphConfig, subGraphSaver);
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig parentConfig) {
        return redisSaver.listSubGraphSaver(parentConfig);
    }

    /**
     * {@link RedisSaver#cleanupThread(String)} takes the saver's internal thread id, which is only
     * reachable through the thread-name key — and release deletes that key, so read it first.
     */
    private Optional<String> activeInternalThreadId(RunnableConfig config) {
        String threadNameKey = keyNamingStrategy.threadNameKey(redisSaver.threadId(config));

        return Optional.ofNullable(redissonClient.<String>getBucket(threadNameKey, StringCodec.INSTANCE).get());
    }
}
