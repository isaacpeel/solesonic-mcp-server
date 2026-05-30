package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnCompetition(
        List<EspnCompetitor> competitors,
        List<EspnNote> notes,
        List<EspnBroadcast> broadcasts,
        EspnStatus status
) implements Serializable {}
