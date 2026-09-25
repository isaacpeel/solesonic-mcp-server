package com.solesonic.agent.checkpoint;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpCheckpointKeyNamingStrategyTest {

    private final McpCheckpointKeyNamingStrategy keyNamingStrategy = new McpCheckpointKeyNamingStrategy();

    @Test
    void everyKeyLivesUnderTheSingleCheckpointPrefix() {
        Set<String> keys = Set.of(
                keyNamingStrategy.threadKey("internal-1"),
                keyNamingStrategy.threadNameKey("agile:chat-1"),
                keyNamingStrategy.checkpointKey("checkpoint-1"),
                keyNamingStrategy.checkpointsKey("internal-1"));

        assertThat(keyNamingStrategy.keyPrefix()).isEqualTo("mcp:checkpoint:");
        assertThat(keys).hasSize(4).allSatisfy(key -> assertThat(key).startsWith("mcp:checkpoint:"));
    }

    @Test
    void threadNameKeyEmbedsTheNamespacedThreadName() {
        assertThat(keyNamingStrategy.threadNameKey("agile:chat-1")).contains("agile:chat-1");
    }
}
