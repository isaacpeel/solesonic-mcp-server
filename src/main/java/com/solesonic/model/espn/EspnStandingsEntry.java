package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStandingsEntry(EspnTeamInfo team, List<EspnStandingStat> stats) implements Serializable {}
