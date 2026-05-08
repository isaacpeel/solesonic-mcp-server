package com.solesonic.mcp.model.espn;

public record EspnCompetitor(
        String homeAway,
        String score,
        EspnTeamInfo team
) {}
