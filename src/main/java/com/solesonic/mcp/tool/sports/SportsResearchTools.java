package com.solesonic.mcp.tool.sports;

import com.solesonic.mcp.workflow.SportsResearchWorkflow;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@SuppressWarnings("unused")
@Service
public class SportsResearchTools {
    private static final Logger log = LoggerFactory.getLogger(SportsResearchTools.class);

    public static final String SPORTS_WORKFLOW = "sports-workflow";

    public static final String SPORTS_RESEARCH = "nba-research";

    private static final String SPORTS_RESEARCH_DESCRIPTION = """
            Research NBA basketball information, from basic schedule lookups to professional-grade
            game previews, player analysis, standings, trade news, and injury reports.

            Supports all query types:
              - Schedule lookups: "When do the Celtics play next?"
              - Game previews: "Break down tonight's Lakers vs Nuggets matchup"
              - Player analysis: "How has Jayson Tatum been playing this month?" (use playerName for deep-dive)
              - Standings: "Where do the Knicks stand in the playoffs right now?"
              - Trade news: "What players were involved in the recent Nets trade?"
              - General news: "What are the latest NBA injury updates?"

            For single-player deep-dive analysis, provide the player's name in the playerName parameter
            to enable advanced game log, usage, splits, and impact metric research.
            """;

    private final SportsResearchWorkflow sportsResearchWorkflow;

    public SportsResearchTools(SportsResearchWorkflow sportsResearchWorkflow) {
        this.sportsResearchWorkflow = sportsResearchWorkflow;
    }

    @PreAuthorize("hasAuthority('ROLE_MCP-WEB-SEARCH')")
    @McpTool(name = SPORTS_RESEARCH, description = SPORTS_RESEARCH_DESCRIPTION)
    public String sportsResearch(
            McpSyncRequestContext mcpSyncRequestContext,
            @McpToolParam(description = """
                    The NBA question to research. Include team names, player names, and any relevant
                    context. Examples: "When do the Warriors play next?", "Break down tonight's Celtics
                    vs Heat game", "How is LeBron James playing this week?", "NBA Eastern Conference
                    standings", "Latest injury news around the league".
                    """)
            String userMessage,
            @McpToolParam(required = false, description = """
                    Optional: the full name of a single NBA player for focused deep-dive analysis.
                    When provided, the workflow runs additional game log, advanced stats, and impact
                    metric searches specifically for this player. Only set this when the user's question
                    is about one specific player (e.g., "Jayson Tatum", "LeBron James").
                    Leave null for team, schedule, standings, trade, or multi-player questions.
                    """)
            String playerName
    ) {
        log.info("Sports research tool invoked: userMessage={}, playerName={}", userMessage, playerName);

        SportsResearchWorkflowContext workflowContext =
                sportsResearchWorkflow.startWorkflow(mcpSyncRequestContext, userMessage, playerName);

        String analysis = workflowContext.getFinalAnalysis();
        if (analysis == null || analysis.isBlank()) {
            return "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";
        }
        return analysis;
    }
}
