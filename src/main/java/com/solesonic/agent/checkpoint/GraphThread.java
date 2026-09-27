package com.solesonic.agent.checkpoint;

import com.solesonic.mcp.security.identity.CallerIdentity;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.RunnableConfig;

import java.util.UUID;

/**
 * The checkpoint thread a graph run is saved under: {@code <graphName>:<conversationId>}.
 * <p>
 * Every graph shares one checkpoint saver, which keys purely by thread id, so thread ids must be
 * namespaced by graph — otherwise two graphs run in the same conversation would overwrite each
 * other's checkpoints. Entry points build their {@link RunnableConfig} only through this type:
 * <ul>
 *   <li>{@link #conversation} for graphs that pause and resume across calls — stable per caller and
 *   conversation, and never shared between callers even if a client reuses a conversation id;</li>
 *   <li>{@link #run} for graphs that always finish in one call — unique per run, so parallel tool
 *   calls in one conversation never write to, or clean up, each other's checkpoints.</li>
 * </ul>
 * Code that runs a sub-graph by hand (a fan-out) derives the child's config with
 * {@link #childConfig(RunnableConfig, String)} so concurrent branches never share a thread.
 */
public record GraphThread(String graphName, String conversationId) {

    private static final String SEPARATOR = ":";

    public static GraphThread conversation(String graphName, CallerIdentity callerIdentity, String conversationId) {
        return new GraphThread(graphName, callerIdentity.userId() + SEPARATOR + conversationId);
    }

    public static GraphThread run(String graphName, CallerIdentity callerIdentity) {
        return new GraphThread(graphName, callerIdentity.userId() + SEPARATOR + UUID.randomUUID());
    }

    public GraphThread {
        if (StringUtils.isBlank(graphName)) {
            throw new IllegalArgumentException("graphName cannot be blank");
        }

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId cannot be blank");
        }
    }

    public String threadId() {
        return graphName + SEPARATOR + conversationId;
    }

    public RunnableConfig.Builder runnableConfigBuilder() {
        return RunnableConfig.builder().threadId(threadId());
    }

    public RunnableConfig runnableConfig() {
        return runnableConfigBuilder().build();
    }

    /**
     * A config for a sub-graph run by hand inside a parent graph's node: inherits the parent's
     * metadata, but checkpoints under {@code <parentThreadId>:<childName>}.
     */
    public static RunnableConfig.Builder childConfig(RunnableConfig parentConfig, String childName) {
        String parentThreadId = parentConfig.threadId()
                .orElseThrow(() -> new IllegalStateException(
                        "Parent graph run has no thread id; start it with a GraphThread before deriving child threads"));

        return RunnableConfig.builder(parentConfig).threadId(parentThreadId + SEPARATOR + childName);
    }
}
