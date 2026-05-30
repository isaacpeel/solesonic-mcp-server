package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStandingsResponse(List<EspnConference> children) implements Serializable {}
