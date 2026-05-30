package com.solesonic.mcp.model.espn;

import java.io.Serializable;

public record EspnTeamInfo(
        String id,
        String abbreviation,
        String displayName
) implements Serializable {}
