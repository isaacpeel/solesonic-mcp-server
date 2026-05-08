package com.solesonic.mcp.model.espn;

import java.util.List;

public record EspnStandingsEntry(EspnTeamInfo team, List<EspnStandingStat> stats) {}
