package com.solesonic.mcp.prompt;

import com.solesonic.mcp.command.ConfluenceCommandProvider;
import com.solesonic.mcp.tool.atlassian.CreateConfluenceTools;
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
public class ConfluencePromptProvider {
    private static final Logger log = LoggerFactory.getLogger(ConfluencePromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String INPUT = "input";
    private static final String AVAILABLE_TOOLS = "available_tools";

    private static final String DESCRIPTION = "Draft and create Confluence pages from natural language descriptions.";

    @Value("classpath:prompt/create_confluence_page_prompt.st")
    private Resource createConfluencePagePrompt;

    @McpPrompt(
            name = "create-confluence-page-prompt",
            title = "Create Confluence Page",
            description = DESCRIPTION,
            metaProvider = ConfluenceCommandProvider.class
    )
    public McpSchema.GetPromptResult createConfluencePagePrompt(
            @McpArg(name = "userMessage", description = "The user's natural language request describing the page to create in Confluence.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting Confluence page creation prompt.");

        String availableToolsList = availableTools(CreateConfluenceTools.class, WebSearchTools.class, DateTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableToolsList
        );

        return buildPromptResult("create-confluence-page-prompt", this.createConfluencePagePrompt, templateVariables);
    }
}
