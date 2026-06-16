package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnStatsResponse(EspnStatsResults results, EspnStatsTeamInfo team) implements Serializable {}
