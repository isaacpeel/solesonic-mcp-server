package com.solesonic.service.espn;

import com.solesonic.model.espn.EspnAthlete;
import com.solesonic.model.espn.EspnCompetition;
import com.solesonic.model.espn.EspnConference;
import com.solesonic.model.espn.EspnEvent;
import com.solesonic.model.espn.EspnRosterResponse;
import com.solesonic.model.espn.EspnScheduleResponse;
import com.solesonic.model.espn.EspnScheduleSummary;
import com.solesonic.model.espn.EspnStandingStat;
import com.solesonic.model.espn.EspnStandingsEntry;
import com.solesonic.model.espn.EspnStandingsResponse;
import com.solesonic.model.espn.EspnStatsCategory;
import com.solesonic.model.espn.EspnStatsResponse;
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
        log.info("Getting schedule summary");

        if (teamAbbreviations == null || teamAbbreviations.isEmpty()) {
            return buildGeneralScheduleSummary();
        }
        return buildTeamScheduleSummary(teamAbbreviations);
    }

    private EspnScheduleSummary buildGeneralScheduleSummary() {
        log.info("building scoreboard summary");

        EspnScheduleResponse espnScheduleResponse = espnClient.fetchScoreboard();

        if (espnScheduleResponse == null || espnScheduleResponse.events() == null || espnScheduleResponse.events().isEmpty()) {
            log.error("ESPN scoreboard returned no events");
            return new EspnScheduleSummary(List.of());
        }

        return new EspnScheduleSummary(espnScheduleResponse.events());
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
                        (first, _) -> first,
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
        if (event.competitions() == null || event.competitions().isEmpty()) {
            return false;
        }

        EspnCompetition competition = event.competitions().getFirst();
        if (competition.status() == null || competition.status().type() == null) {
            return false;
        }
        return "post".equals(competition.status().type().state());
    }

    public String getRosterData(List<String> teamAbbreviations) {
        if (teamAbbreviations == null || teamAbbreviations.isEmpty()) {
            return "No team roster data available.";
        }
        StringBuilder builder = new StringBuilder();
        for (String teamAbbreviation : teamAbbreviations) {
            EspnRosterResponse response = espnClient.fetchTeamRoster(teamAbbreviation);
            if (response == null || response.athletes() == null || response.athletes().isEmpty()) {
                log.warn("ESPN roster returned no athletes for team: {}", teamAbbreviation);
                builder.append("No roster data available for ").append(teamAbbreviation.toUpperCase()).append(".\n\n");
                continue;
            }
            builder.append("=== ESPN Roster: ").append(teamAbbreviation.toUpperCase()).append(" ===\n");
            for (EspnAthlete athlete : response.athletes()) {
                String position = athlete.position() != null ? athlete.position().abbreviation() : "?";
                String jersey = athlete.jersey() != null ? "#" + athlete.jersey() : "";
                builder.append(jersey).append(" ").append(athlete.fullName()).append(" (").append(position).append(")\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public String getStandingsData() {
        EspnStandingsResponse response = espnClient.fetchStandings();
        if (response == null || response.children() == null || response.children().isEmpty()) {
            log.warn("ESPN standings returned no data");
            return "No standings data available.";
        }
        StringBuilder builder = new StringBuilder();
        for (EspnConference conference : response.children()) {
            builder.append("=== ").append(conference.name()).append(" ===\n");
            if (conference.standings() == null || conference.standings().entries() == null) {
                continue;
            }
            for (EspnStandingsEntry entry : conference.standings().entries()) {
                if (entry.team() == null) {
                    continue;
                }
                String wins = findStatDisplayValue(entry.stats(), "wins");
                String losses = findStatDisplayValue(entry.stats(), "losses");
                String winPercent = findStatDisplayValue(entry.stats(), "winPercent");
                builder.append(entry.team().displayName())
                        .append(": ").append(wins).append("-").append(losses)
                        .append(" (").append(winPercent).append(")\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    @SuppressWarnings("unused")
    public String getStatsData(List<String> teamAbbreviations) {
        if (teamAbbreviations == null || teamAbbreviations.isEmpty()) {
            return "No team stats data available.";
        }
        StringBuilder builder = new StringBuilder();
        for (String teamAbbreviation : teamAbbreviations) {
            EspnStatsResponse response = espnClient.fetchTeamStats(teamAbbreviation);
            if (response == null) {
                log.warn("ESPN stats returned no data for team: {}", teamAbbreviation);
                builder.append("No stats available for ").append(teamAbbreviation.toUpperCase()).append(".\n\n");
                continue;
            }
            String teamLabel = response.team() != null ? response.team().displayName() : teamAbbreviation.toUpperCase();
            String record = response.team() != null && response.team().recordSummary() != null
                    ? " (" + response.team().recordSummary() + ")" : "";
            builder.append("=== ESPN Stats: ").append(teamLabel).append(record).append(" ===\n");

            if (response.results() != null && response.results().stats() != null
                    && response.results().stats().categories() != null) {
                for (EspnStatsCategory category : response.results().stats().categories()) {
                    builder.append(category.displayName()).append(":\n");
                    for (var stat : category.stats()) {
                        builder.append("  ").append(stat.abbreviation()).append(": ").append(stat.displayValue()).append("\n");
                    }
                }
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String findStatDisplayValue(List<EspnStandingStat> stats, String statName) {
        if (stats == null) {
            return "?";
        }
        return stats.stream()
                .filter(stat -> statName.equals(stat.name()))
                .findFirst()
                .map(EspnStandingStat::displayValue)
                .orElse("?");
    }
}
