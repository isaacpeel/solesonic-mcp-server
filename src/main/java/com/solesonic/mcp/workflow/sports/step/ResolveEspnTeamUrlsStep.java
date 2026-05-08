package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.EspnTeamRegistry;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResolveEspnTeamUrlsStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "resolve-espn-team-urls";

    private static final Logger log = LoggerFactory.getLogger(ResolveEspnTeamUrlsStep.class);

    private final EspnTeamRegistry espnTeamRegistry;

    public ResolveEspnTeamUrlsStep(EspnTeamRegistry espnTeamRegistry) {
        this.espnTeamRegistry = espnTeamRegistry;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.RESOLVING_TEAM_URLS);
        executionContext.progressTracker().step(name()).update(0.1, "Resolving ESPN team URLs");

        SportsQueryIntent intent = context.getSportsQueryIntent();
        if (intent == null || !intent.hasTeams()) {
            log.info("No teams in intent — skipping ESPN team URL resolution");
            context.setResolvedTeams(List.of());
            executionContext.progressTracker().step(name()).done("No teams to resolve");
            return WorkflowDecision.continueWorkflow();
        }

        List<EspnTeamProfile> resolvedTeams = new ArrayList<>();
        List<String> unresolvedTeams = new ArrayList<>();

        for (String teamName : intent.teams()) {
            espnTeamRegistry.findByName(teamName).ifPresentOrElse(
                    resolvedTeams::add,
                    () -> unresolvedTeams.add(teamName)
            );
        }

        if (!unresolvedTeams.isEmpty()) {
            log.warn("Could not resolve ESPN URLs for teams: {}", unresolvedTeams);
        }

        log.info("Resolved {} of {} teams to ESPN URLs: {}",
                resolvedTeams.size(), intent.teams().size(),
                resolvedTeams.stream().map(EspnTeamProfile::fullName).toList());

        context.setResolvedTeams(resolvedTeams);
        executionContext.progressTracker().step(name()).done(
                "Resolved teams: %s".formatted(
                        resolvedTeams.stream().map(EspnTeamProfile::abbreviation).toList()
                )
        );
        return WorkflowDecision.continueWorkflow();
    }
}
