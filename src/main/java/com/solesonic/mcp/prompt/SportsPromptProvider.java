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

    private static final String DESCRIPTION = """
            A specialized prompt for researching sports information using live web search. Use this when the user asks
            any sports question, from basic schedule lookups to detailed game analysis and player statistics.

            Typical use cases:
            - "When are the Celtics playing next?"
            - "Who might win tonight's Lakers game?"
            - "How has Jayson Tatum been playing lately?"
            - "Where do the Patriots stand in the AFC East?"
            - "Any injury news for the Red Sox?"
            - "Break down the matchup between the Chiefs and Eagles."

            This prompt is appropriate for questions about NBA, NFL, MLB, NHL, MLS, and other major sports leagues.
            It uses live web search internally to retrieve current schedules, injury reports, and statistics — making
            it far more accurate than relying on the model's training data alone.

            Do not use this prompt for non-sports questions or general news unrelated to sports.
            """;

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
