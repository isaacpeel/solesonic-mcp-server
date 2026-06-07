package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStatsCategory(String name, String displayName, List<EspnStatValue> stats) implements Serializable {}
