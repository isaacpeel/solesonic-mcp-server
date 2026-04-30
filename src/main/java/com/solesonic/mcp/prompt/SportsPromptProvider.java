package com.solesonic.mcp.prompt;

import com.solesonic.mcp.command.SportsCommandProvider;
import com.solesonic.mcp.tool.sports.SportsResearchTools;
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
public class SportsPromptProvider {
    private static final Logger log = LoggerFactory.getLogger(SportsPromptProvider.class);

    private static final String AGENT_NAME = "agentName";
    private static final String INPUT = "input";
    private static final String AVAILABLE_TOOLS = "available_tools";

    private static final String DESCRIPTION = "Research sports schedules, game previews, player stats, and news using live web search.";

    @Value("classpath:prompt/sports_prompt.st")
    private Resource sportsPrompt;

    @McpPrompt(
            name = "sports-prompt",
            title = "Sports Research",
            description = DESCRIPTION,
            metaProvider = SportsCommandProvider.class
    )
    public McpSchema.GetPromptResult sportsPrompt(
            @McpArg(name = "userMessage", description = "The user's sports question — schedule lookup, game preview, player analysis, standings, or news.") String userMessage,
            @McpArg(name = "agentName", description = "The name of the agent the user is interacting with.") String agentName
    ) {
        log.info("Getting sports research prompt.");

        String availableToolsList = availableTools(SportsResearchTools.class);

        Map<String, Object> templateVariables = Map.of(
                AGENT_NAME, agentName,
                INPUT, userMessage,
                AVAILABLE_TOOLS, availableToolsList
        );

        return buildPromptResult("sports-prompt", this.sportsPrompt, templateVariables);
    }
}
