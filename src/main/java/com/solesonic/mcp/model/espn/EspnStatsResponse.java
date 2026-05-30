package com.solesonic.mcp.model.espn;

import java.io.Serializable;

public record EspnStatsResponse(EspnStatsResults results, EspnStatsTeamInfo team) implements Serializable {}
