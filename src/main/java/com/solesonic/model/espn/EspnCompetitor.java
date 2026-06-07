package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnCompetitor(
        String homeAway,
        String score,
        EspnTeamInfo team
) implements Serializable {}
