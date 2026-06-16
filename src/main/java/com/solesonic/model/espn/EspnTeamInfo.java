package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnTeamInfo(
        String id,
        String abbreviation,
        String displayName
) implements Serializable {}
