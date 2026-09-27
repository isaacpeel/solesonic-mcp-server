package com.solesonic.agent.checkpoint;

import com.solesonic.mcp.security.identity.CallerIdentity;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphThreadTest {

    private static final UUID USER_ID = UUID.fromString("7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11");
    private static final CallerIdentity CALLER = new CallerIdentity(USER_ID);
    private static final CallerIdentity OTHER_CALLER = new CallerIdentity(UUID.fromString("0b6c1f7e-9d35-4c6e-8a0f-3b2f1c9d8e77"));

    @Test
    void threadIdIsNamespacedByGraph() {
        assertThat(new GraphThread("agile", "chat-1").threadId()).isEqualTo("agile:chat-1");
        assertThat(new GraphThread("jira-create", "chat-1").threadId()).isEqualTo("jira-create:chat-1");
    }

    @Test
    void conversationThread_isStablePerCallerAndConversation() {
        GraphThread first = GraphThread.conversation("jira-create", CALLER, "chat-1");
        GraphThread second = GraphThread.conversation("jira-create", CALLER, "chat-1");

        assertThat(first.threadId()).isEqualTo("jira-create:" + USER_ID + ":chat-1").isEqualTo(second.threadId());
    }

    @Test
    void conversationThread_isNeverSharedBetweenCallers() {
        assertThat(GraphThread.conversation("jira-create", CALLER, "chat-1").threadId())
                .isNotEqualTo(GraphThread.conversation("jira-create", OTHER_CALLER, "chat-1").threadId());
    }

    @Test
    void runThread_isUniquePerRunSoParallelRunsNeverShareCheckpoints() {
        GraphThread first = GraphThread.run("agile", CALLER);
        GraphThread second = GraphThread.run("agile", CALLER);

        assertThat(first.threadId()).startsWith("agile:" + USER_ID + ":");
        assertThat(first.threadId()).isNotEqualTo(second.threadId());
    }

    @Test
    void runnableConfigCarriesTheThreadId() {
        RunnableConfig runnableConfig = new GraphThread("agile", "chat-1").runnableConfig();

        assertThat(runnableConfig.threadId()).contains("agile:chat-1");
    }

    @Test
    void childConfig_nestsUnderTheParentThreadId() {
        RunnableConfig parentConfig = new GraphThread("nba", "context-9").runnableConfig();

        RunnableConfig childConfig = GraphThread.childConfig(parentConfig, "standings").build();

        assertThat(childConfig.threadId()).contains("nba:context-9:standings");
    }

    @Test
    void childConfig_ofAParentWithoutAThread_isRejected() {
        RunnableConfig parentConfig = RunnableConfig.builder().build();

        assertThatThrownBy(() -> GraphThread.childConfig(parentConfig, "standings"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankParts_areRejected() {
        assertThatThrownBy(() -> new GraphThread(" ", "chat-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GraphThread("agile", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
