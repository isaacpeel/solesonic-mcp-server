package com.solesonic.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@Service
public class PromptProvider {
    private static final Logger log =  LoggerFactory.getLogger(PromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String USER_MESSAGE = "userMessage";

    @Value("classpath:prompt/basic-prompt.st")
    private Resource basicPrompt;

    @McpPrompt(name = "basic-prompt")
    public McpSchema.GetPromptResult basicPrompt(@McpArg(name="userMessage", description = "A message from the user to embed into this prompt.") String userMessage,
                                                 @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName) {
        log.info("Getting basic prompt...");

        Map<String, Object> promptContext = Map.of(AGENT_NAME, agentName,
                USER_MESSAGE, userMessage);

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(basicPrompt)
                .variables(promptContext)
                .build();

        Prompt prompt = promptTemplate.create();

        UserMessage promptUserMessage = prompt.getUserMessage();
        String promptText = promptUserMessage.getText();

        McpSchema.TextContent textContent = new McpSchema.TextContent(promptText);
        McpSchema.PromptMessage promptMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, textContent);

        return new McpSchema.GetPromptResult("basic-prompt", List.of(promptMessage));
    }
}
