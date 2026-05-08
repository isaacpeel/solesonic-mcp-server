package com.solesonic.mcp.workflow.sports.model;

/**
 * ESPN URL profile for a single NBA team. All URLs are constructed from the authoritative
 * ESPN pattern: /nba/team/{section}/_/name/{abbreviation}/{slug}
 */
public record EspnTeamProfile(
        String fullName,
        String abbreviation,
        String urlSlug,
        String scheduleUrl,
        String rosterUrl,
        String statsUrl
) {
    private static final String ESPN_TEAM_BASE = "https://www.espn.com/nba/team/%s/_/name/%s/%s";

    public static EspnTeamProfile of(String fullName, String abbreviation, String urlSlug) {
        return new EspnTeamProfile(
                fullName,
                abbreviation,
                urlSlug,
                ESPN_TEAM_BASE.formatted("schedule", abbreviation, urlSlug),
                ESPN_TEAM_BASE.formatted("roster", abbreviation, urlSlug),
                ESPN_TEAM_BASE.formatted("stats", abbreviation, urlSlug)
        );
    }
}
