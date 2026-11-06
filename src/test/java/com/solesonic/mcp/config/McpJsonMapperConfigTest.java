package com.solesonic.mcp.config;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serializes elicitation requests through the same {@code mcpServerJsonMapper} bean the MCP
 * transport uses. The other elicitation tests mock the exchange, so only this catches Jackson 3.2.x
 * failing with "Conflict between type id property 'mode' and bean property with same name", which
 * left every elicitation (assignee picker, delete confirmations) unsent until the request timeout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class McpJsonMapperConfigTest {

    @Autowired
    @Qualifier("mcpServerJsonMapper")
    private JsonMapper mcpServerJsonMapper;

    @Test
    void formElicitationSerializesWithModeDiscriminatorExactlyOnce() throws Exception {
        ElicitFormRequest elicitRequest = ElicitFormRequest.builder("Who should this story be assigned to?",
                        Map.of("type", "object", "properties", Map.of()))
                .meta(Map.of("chatId", "chat-1"))
                .build();

        String json = serialize(elicitRequest);

        assertThat(json).contains("\"mode\":\"form\"");
        assertThat(json.split("\"mode\"", -1)).hasSize(2);
    }

    @Test
    void urlElicitationSerializesWithModeDiscriminator() throws Exception {
        ElicitUrlRequest elicitRequest = ElicitUrlRequest.builder("Sign in", "https://example.com/auth", "elicitation-1")
                .build();

        assertThat(serialize(elicitRequest)).contains("\"mode\":\"url\"");
    }

    private String serialize(Object params) throws Exception {
        JSONRPCRequest request = new JSONRPCRequest("2.0", "elicitation/create", "1", params);

        return new JacksonMcpJsonMapper(mcpServerJsonMapper).writeValueAsString(request);
    }
}
