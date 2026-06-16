package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnStatsTeamInfo(
        String abbreviation,
        String displayName,
        String recordSummary,
        String standingSummary
) implements Serializable {}
