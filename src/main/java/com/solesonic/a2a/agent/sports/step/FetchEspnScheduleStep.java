package com.solesonic.a2a.agent.sports.step;

import com.solesonic.mcp.model.espn.EspnScheduleSummary;
import com.solesonic.mcp.service.espn.EspnService;
import com.solesonic.a2a.agent.sports.SportsState;
import com.solesonic.a2a.agent.sports.model.EspnTeamProfile;
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

    public EspnScheduleSummary fetch(SportsState state) {
        List<EspnTeamProfile> resolvedTeams = state.resolvedTeams().orElse(List.of());

        List<String> teamAbbreviations = resolvedTeams.isEmpty()
                ? List.of()
                : resolvedTeams.stream().map(EspnTeamProfile::abbreviation).toList();

        return fetchForTeams(teamAbbreviations);
    }

    private EspnScheduleSummary fetchForTeams(List<String> teamAbbreviations) {
        log.info("Fetching ESPN schedule via API. Teams: {}", teamAbbreviations);

        try {
            return espnService.getScheduleSummary(teamAbbreviations);
        } catch (Exception exception) {
            log.warn("ESPN schedule API fetch failed: {}", exception.getMessage());
            return new EspnScheduleSummary(List.of());
        }
    }
}
