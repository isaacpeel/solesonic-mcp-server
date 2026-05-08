package com.solesonic.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public class PromptUtil {
    public static McpSchema.GetPromptResult buildPromptResult(
            String promptName,
            Resource templateResource,
            Map<String, Object> templateVariables
    ) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(templateResource)
                .variables(templateVariables)
                .build();

        Prompt prompt = promptTemplate.create();

        UserMessage promptUserMessage = prompt.getUserMessage();
        String promptText = promptUserMessage.getText();

        McpSchema.TextContent textContent = new McpSchema.TextContent(promptText);
        McpSchema.PromptMessage promptMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, textContent);

        return new McpSchema.GetPromptResult(promptName, List.of(promptMessage));
    }
}
