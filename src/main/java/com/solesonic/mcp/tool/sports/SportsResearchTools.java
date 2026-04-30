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
            Research NBA basketball information, from basic schedule lookups to detailed game and player analysis.
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
                    The NBA question to research. Can range from simple schedule lookups
                    ("When do the Celtics play next?") to complex analysis
                    ("Compare Tatum and Durant's recent stats for tonight's game").
                    Include team names, player names, and any relevant context.
                    """)
            String userMessage
    ) {
        log.info("Sports research tool invoked: {}", userMessage);
        SportsResearchWorkflowContext workflowContext =
                sportsResearchWorkflow.startWorkflow(mcpSyncRequestContext, userMessage);

        String analysis = workflowContext.getFinalAnalysis();
        if (analysis == null || analysis.isBlank()) {
            return "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";
        }
        return analysis;
    }
}
