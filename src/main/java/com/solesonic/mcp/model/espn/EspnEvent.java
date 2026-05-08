package com.solesonic.mcp.model.espn;

import java.util.List;

public record EspnEvent(
        String id,
        String date,
        String shortName,
        List<EspnCompetition> competitions,
        EspnStatus status
) {}
