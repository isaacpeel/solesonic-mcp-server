package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStatsData(List<EspnStatsCategory> categories) implements Serializable {}
