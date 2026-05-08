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
            Research current NBA basketball information: schedules, game previews, player analysis,
            standings, trades, and injury reports.

            Call this tool for ANY NBA question — including follow-up questions and predictions about
            games or players that came up earlier in the conversation. Do NOT answer NBA schedule,
            roster, or recent-performance questions from training data; always invoke this tool so
            the response is grounded in current sources.

            This tool has no memory of prior conversation turns. Include ALL relevant context in
            userMessage (team names, player names, game date, series situation) every time, even for
            follow-ups.

            Examples:
              - "When do the Celtics play next?"
              - "Break down tonight's Lakers vs Nuggets matchup"
              - "How has Jayson Tatum been playing this month?"
              - "Where do the Knicks stand in the playoffs right now?"
              - "Predict the winner of game 6 of the Celtics-Heat series"
              - "Latest NBA injury news"
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
                    The NBA question to research, with all context the tool needs to answer it
                    standalone (team names, player names, game date, series state, etc.). Examples:
                    "When do the Warriors play next?", "Break down tonight's Celtics vs Heat game",
                    "How is LeBron James playing this week?", "NBA Eastern Conference standings".
                    """)
            String userMessage
    ) {
        log.info("NBA research tool invoked: userMessage={}", userMessage);

        SportsResearchWorkflowContext workflowContext =
                sportsResearchWorkflow.startWorkflow(mcpSyncRequestContext, userMessage);

        String analysis = workflowContext.getFinalAnalysis();
        if (analysis == null || analysis.isBlank()) {
            return "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";
        }
        return analysis;
    }
}
