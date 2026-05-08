package com.solesonic.mcp.model.espn;

import java.util.List;

public record EspnCompetition(
        List<EspnCompetitor> competitors,
        List<EspnNote> notes,
        List<EspnBroadcast> broadcasts,
        EspnStatus status
) {}
