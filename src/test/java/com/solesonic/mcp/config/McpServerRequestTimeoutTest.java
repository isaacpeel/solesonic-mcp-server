package com.solesonic.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the elicitation delivery failure observed in prod-nginx on
 * 2026-08-31 (see {@code ai-scratch/agile-tool/user-story-elicitation-delivery-failure.txt}).
 *
 * <p>{@code spring.ai.mcp.server.request-timeout} was configured only in the {@code local}
 * profile, so production ran on Spring AI's 20 second default. That timeout is the one applied
 * to server-initiated requests in
 * {@code McpStreamableServerSession.McpStreamableServerSessionStream#sendRequest}, which means a
 * user who took longer than 20 seconds to answer an elicitation prompt had their pending response
 * sink discarded. When the answer finally arrived,
 * {@code McpStreamableServerSession#accept(JSONRPCResponse)} no longer recognised the request id
 * and {@code WebMvcStreamableServerTransportProvider#handlePost} turned that into an HTTP 500 —
 * the client saw the elicitation as answered while the server never applied it.
 *
 * <p>Elicitation prompts are answered by a human, so the timeout has to be measured in minutes.
 * This test pins the effective value so the setting cannot silently drift back to the default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class McpServerRequestTimeoutTest {

    /**
     * Server-initiated requests wait on a person, not on a machine. Anything shorter than this
     * turns a slow-but-normal human answer into a 500.
     */
    private static final Duration MINIMUM_HUMAN_RESPONSE_WINDOW = Duration.ofMinutes(5);

    @Autowired
    private McpServerProperties mcpServerProperties;

    @Test
    void requestTimeoutLeavesAHumanTimeToAnswerAnElicitationPrompt() {
        assertThat(mcpServerProperties.getRequestTimeout())
                .as("spring.ai.mcp.server.request-timeout must be configured in application.properties "
                        + "so every profile inherits it, not just 'local'")
                .isGreaterThanOrEqualTo(MINIMUM_HUMAN_RESPONSE_WINDOW);
    }
}
