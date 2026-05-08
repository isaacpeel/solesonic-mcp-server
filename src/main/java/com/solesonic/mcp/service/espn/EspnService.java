package com.solesonic.mcp.service.espn;

import com.solesonic.mcp.model.espn.EspnCompetition;
import com.solesonic.mcp.model.espn.EspnEvent;
import com.solesonic.mcp.model.espn.EspnScheduleResponse;
import com.solesonic.mcp.model.espn.EspnScheduleSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EspnService {

    private static final Logger log = LoggerFactory.getLogger(EspnService.class);

    private final EspnClient espnClient;

    public EspnService(EspnClient espnClient) {
        this.espnClient = espnClient;
    }

    public EspnScheduleSummary getScheduleSummary(List<String> teamAbbreviations) {
        if (teamAbbreviations == null || teamAbbreviations.isEmpty()) {
            return buildScoreboardSummary();
        }
        return buildTeamScheduleSummary(teamAbbreviations);
    }

    private EspnScheduleSummary buildScoreboardSummary() {
        EspnScheduleResponse response = espnClient.fetchScoreboard();
        if (response == null || response.events() == null || response.events().isEmpty()) {
            log.warn("ESPN scoreboard returned no events");
            return new EspnScheduleSummary(List.of());
        }
        return new EspnScheduleSummary(response.events());
    }

    private EspnScheduleSummary buildTeamScheduleSummary(List<String> teamAbbreviations) {
        List<EspnEvent> allEvents = teamAbbreviations.stream()
                .map(espnClient::fetchTeamSchedule)
                .filter(response -> response != null && response.events() != null)
                .flatMap(response -> response.events().stream())
                .filter(event -> !isCompleted(event))
                .collect(Collectors.toMap(
                        EspnEvent::id,
                        event -> event,
                        (first, second) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(EspnEvent::date))
                .toList();

        if (allEvents.isEmpty()) {
            log.warn("ESPN team schedule returned no upcoming events for teams: {}", teamAbbreviations);
        }

        return new EspnScheduleSummary(allEvents);
    }

    private boolean isCompleted(EspnEvent event) {
        if (event.competitions() == null || event.competitions().isEmpty()) return false;
        EspnCompetition competition = event.competitions().get(0);
        if (competition.status() == null || competition.status().type() == null) return false;
        return "post".equals(competition.status().type().state());
    }
}
