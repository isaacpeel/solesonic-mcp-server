package com.solesonic.mcp.config.espn;

public final class EspnConstants {

    private EspnConstants() {}

    public static final String ESPN_API_WEB_CLIENT = "espnApiWebClient";

    public static final String SCOREBOARD_ENDPOINT = "/apis/site/v2/sports/basketball/nba/scoreboard";
    public static final String TEAM_SCHEDULE_ENDPOINT = "/apis/site/v2/sports/basketball/nba/teams/{teamAbbreviation}/schedule";
    public static final String ALL_TEAMS_ENDPOINT = "/apis/site/v2/sports/basketball/nba/teams";
}
