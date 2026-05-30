package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStatsCategory(String name, String displayName, List<EspnStatValue> stats) implements Serializable {}
