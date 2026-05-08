package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.service.espn.EspnService;
import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.solesonic.mcp.workflow.sports.SportsWorkflowStage.FETCHING_ESPN_STANDINGS;

@Component
public class FetchEspnStandingsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "fetch-espn-standings";

    private static final Logger log = LoggerFactory.getLogger(FetchEspnStandingsStep.class);

    private static final Set<SportsQuestionType> STANDINGS_RELEVANT_TYPES = Set.of(
            SportsQuestionType.STANDINGS,
            SportsQuestionType.GAME_PREVIEW
    );

    private final EspnService espnService;

    public FetchEspnStandingsStep(EspnService espnService) {
        this.espnService = espnService;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public boolean isParallelSafe() {
        return true;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext sportsResearchWorkflowContext, WorkflowExecutionContext workflowExecutionContext) {
        List<SportsQuestionType> questionTypes = sportsResearchWorkflowContext.getSportsQueryIntent().questionTypes();

        boolean isStandingsQuestion = questionTypes.stream()
                .anyMatch(STANDINGS_RELEVANT_TYPES::contains);

        if (!isStandingsQuestion) {
            log.info("Skipping standings fetch for question type: {}", questionTypes);
            return WorkflowDecision.skip("Standings not needed for question type: " + questionTypes);
        }

        sportsResearchWorkflowContext.setCurrentStage(FETCHING_ESPN_STANDINGS);
        workflowExecutionContext.progressTracker().step(name()).update(0.2, "Fetching NBA standings from ESPN");

        log.info("Fetching ESPN standings via API");

        try {
            String standingsData = espnService.getStandingsData();
            sportsResearchWorkflowContext.setEspnStandingsData(standingsData);
            workflowExecutionContext.progressTracker().step(name()).done("ESPN standings data fetched");
            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to fetch ESPN standings data", exception);
            sportsResearchWorkflowContext.setEspnStandingsData("ESPN standings data unavailable.");
            return WorkflowDecision.continueWorkflow();
        }
    }
}
