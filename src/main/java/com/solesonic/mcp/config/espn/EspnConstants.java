package com.solesonic.mcp.config.espn;

public final class EspnConstants {

    private EspnConstants() {}

    public static final String ESPN_API_WEB_CLIENT = "espnApiWebClient";

    public static final String SCOREBOARD_ENDPOINT = "/apis/site/v2/sports/basketball/nba/scoreboard";
    public static final String TEAM_SCHEDULE_ENDPOINT = "/apis/site/v2/sports/basketball/nba/teams/{teamAbbreviation}/schedule";
    public static final String TEAM_ROSTER_ENDPOINT = "/apis/site/v2/sports/basketball/nba/teams/{teamAbbreviation}/roster";
    public static final String STANDINGS_ENDPOINT = "/apis/v2/sports/basketball/nba/standings";
    public static final String TEAM_STATS_ENDPOINT = "/apis/site/v2/sports/basketball/nba/teams/{teamAbbreviation}/statistics";
}
