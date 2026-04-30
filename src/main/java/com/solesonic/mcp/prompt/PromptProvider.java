package com.solesonic.mcp.prompt;

import com.solesonic.mcp.command.DefaultCommandProvider;
import com.solesonic.mcp.service.WeatherService;
import com.solesonic.mcp.tool.general.DateTools;
import com.solesonic.mcp.tool.tavily.WebSearchTools;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptUtil.buildPromptResult;
import static com.solesonic.mcp.tool.SolesonicTool.availableTools;

@SuppressWarnings("unused")
@Service
public class PromptProvider {
    private static final Logger log = LoggerFactory.getLogger(PromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String USER_MESSAGE = "userMessage";
    private static final String AVAILABLE_TOOLS = "available_tools";
    public static final String COMMAND = "command";

    private static final String DESCRIPTION = "General-purpose assistant for coding, writing, brainstorming, and everyday questions.";

    @Value("classpath:prompt/basic-prompt.st")
    private Resource basicPrompt;

    @McpPrompt(
            name = "basic-prompt",
            title = "General Assistant",
            description = DESCRIPTION,
            metaProvider = DefaultCommandProvider.class)
    public McpSchema.GetPromptResult basicPrompt(
            @McpArg(name = USER_MESSAGE, description = "A message from the user to embed into this prompt.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting basic prompt.");

        String availableToolsList = availableTools(WeatherService.class, WebSearchTools.class, DateTools.class);

        Map<String, Object> promptContext = Map.of(
                AGENT_NAME, agentName,
                USER_MESSAGE, userMessage,
                AVAILABLE_TOOLS, availableToolsList
        );

        return buildPromptResult("basic-prompt", this.basicPrompt, promptContext);
    }
}
