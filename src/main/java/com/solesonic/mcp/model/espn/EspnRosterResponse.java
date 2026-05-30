package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnRosterResponse(List<EspnAthlete> athletes) implements Serializable {}
