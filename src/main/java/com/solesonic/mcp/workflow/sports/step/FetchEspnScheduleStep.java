package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.model.espn.EspnScheduleSummary;
import com.solesonic.mcp.service.espn.EspnService;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchEspnScheduleStep {

    private static final Logger log = LoggerFactory.getLogger(FetchEspnScheduleStep.class);

    private final EspnService espnService;

    public FetchEspnScheduleStep(EspnService espnService) {
        this.espnService = espnService;
    }

    public EspnScheduleSummary fetch(SportsResearchWorkflowContext context) {
        List<EspnTeamProfile> resolvedTeams = context.getResolvedTeams();

        List<String> teamAbbreviations = (resolvedTeams != null && !resolvedTeams.isEmpty())
                ? resolvedTeams.stream().map(EspnTeamProfile::abbreviation).toList()
                : List.of();

        log.info("Fetching ESPN schedule via API. Teams: {}", teamAbbreviations);

        try {
            return espnService.getScheduleSummary(teamAbbreviations);
        } catch (Exception exception) {
            log.warn("ESPN schedule API fetch failed: {}", exception.getMessage());
            return new EspnScheduleSummary(List.of());
        }
    }
}
