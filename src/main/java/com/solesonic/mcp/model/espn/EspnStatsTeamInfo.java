package com.solesonic.mcp.model.espn;

public record EspnStatsTeamInfo(
        String abbreviation,
        String displayName,
        String recordSummary,
        String standingSummary
) {}
