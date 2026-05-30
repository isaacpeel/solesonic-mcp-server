package com.solesonic.mcp.model.espn;

import com.solesonic.mcp.prompt.PromptConstants;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public record EspnScheduleSummary(List<EspnEvent> games) implements Serializable {

    public boolean hasGames() {
        return games != null && !games.isEmpty();
    }

    public boolean hasUpcomingOrLiveGames() {
        if (games == null) {
            return false;
        }
        return games.stream()
                .anyMatch(event -> {
                    String state = stateOf(event);
                    return "pre".equals(state) || "in".equals(state);
                });
    }

    public List<EspnEvent> liveGames() {
        if (games == null) {
            return List.of();
        }
        return games.stream()
                .filter(event -> "in".equals(stateOf(event)))
                .toList();
    }

    public Optional<EspnEvent> nextUpcomingGame() {
        if (games == null) {
            return Optional.empty();
        }
        return games.stream()
                .filter(event -> "pre".equals(stateOf(event)))
                .min(java.util.Comparator.comparing(EspnEvent::date));
    }

    public String toFormattedString() {
        if (!hasGames()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("=== NBA Schedule (ESPN) ===\n\n");
        for (EspnEvent event : games) {
            builder.append(formatEvent(event)).append("\n");
        }
        return builder.toString();
    }

    private String stateOf(EspnEvent event) {
        EspnCompetition competition = firstCompetition(event);
        if (competition == null || competition.status() == null || competition.status().type() == null) {
            return null;
        }
        return competition.status().type().state();
    }

    private String formatEvent(EspnEvent event) {
        EspnCompetition competition = firstCompetition(event);
        if (competition == null) {
            return event.shortName() != null ? event.shortName() : event.date();
        }

        String dateLabel = formatDate(event.date());
        String matchup = buildMatchup(competition.competitors());
        String statusDetail = buildStatusDetail(competition.status());
        String broadcast = buildBroadcast(competition.broadcasts());
        String seriesInfo = buildSeriesInfo(competition.notes());

        return Stream.of(dateLabel, matchup, statusDetail, broadcast, seriesInfo)
                .filter(segment -> segment != null && !segment.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private EspnCompetition firstCompetition(EspnEvent event) {
        if (event.competitions() == null || event.competitions().isEmpty()) {
            return null;
        }

        return event.competitions().getFirst();
    }

    private String buildMatchup(List<EspnCompetitor> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return null;
        }

        String awayAbbreviation = competitors.stream()
                .filter(competitor -> "away".equals(competitor.homeAway()))
                .findFirst()
                .map(competitor -> competitor.team() != null ? competitor.team().abbreviation() : "")
                .orElse("");

        String homeAbbreviation = competitors.stream()
                .filter(competitor -> "home".equals(competitor.homeAway()))
                .findFirst()
                .map(competitor -> competitor.team() != null ? competitor.team().abbreviation() : "")
                .orElse("");

        return awayAbbreviation + " @ " + homeAbbreviation;
    }

    private String formatDate(String isoDate) {
        if (isoDate == null) {
            return null;
        }
        try {
            ZonedDateTime dateTime = ZonedDateTime.parse(isoDate, DateTimeFormatter.ISO_DATE_TIME);
            return PromptConstants.formatDateTime(dateTime);
        } catch (Exception exception) {
            return isoDate;
        }
    }

    private String buildStatusDetail(EspnStatus status) {
        if (status == null || status.type() == null) {
            return null;
        }
        return status.type().shortDetail();
    }

    private String buildBroadcast(List<EspnBroadcast> broadcasts) {
        if (broadcasts == null || broadcasts.isEmpty()) {
            return null;
        }
        return broadcasts.stream()
                .filter(broadcast -> broadcast.names() != null)
                .flatMap(broadcast -> broadcast.names().stream())
                .collect(Collectors.joining(", "));
    }

    private String buildSeriesInfo(List<EspnNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return null;
        }
        return notes.stream()
                .map(EspnNote::headline)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
    }
}
