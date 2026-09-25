package com.solesonic.agent.checkpoint;

import org.bsc.langgraph4j.checkpoint.KeyNamingStrategy;

/**
 * Keeps every graph checkpoint key under one {@code mcp:checkpoint:} keyspace, visually distinct
 * from the A2A task store's {@code a2a:} keys and chat memory's {@code solesonic:chat:memory:} keys.
 */
public class McpCheckpointKeyNamingStrategy implements KeyNamingStrategy {

    private static final String PREFIX = "mcp:checkpoint:";
    private static final String THREAD = "thread:";
    private static final String THREAD_NAME = "thread-name:";
    private static final String ACTIVE_SUFFIX = ":active";
    private static final String STATE = "state:";
    private static final String CHECKPOINTS_SUFFIX = ":checkpoints";

    @Override
    public String threadKey(String threadId) {
        return PREFIX + THREAD + threadId;
    }

    @Override
    public String threadNameKey(String threadName) {
        return PREFIX + THREAD_NAME + threadName + ACTIVE_SUFFIX;
    }

    @Override
    public String checkpointKey(String checkpointId) {
        return PREFIX + STATE + checkpointId;
    }

    @Override
    public String checkpointsKey(String threadId) {
        return threadKey(threadId) + CHECKPOINTS_SUFFIX;
    }

    @Override
    public String keyPrefix() {
        return PREFIX;
    }
}
