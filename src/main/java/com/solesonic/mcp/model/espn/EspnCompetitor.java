package com.solesonic.mcp.model.espn;

import java.io.Serializable;

public record EspnCompetitor(
        String homeAway,
        String score,
        EspnTeamInfo team
) implements Serializable {}
