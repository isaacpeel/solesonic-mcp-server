package com.solesonic.mcp.model.espn;

import java.io.Serializable;

public record EspnStatValue(String name, String displayName, String abbreviation, String displayValue) implements Serializable {}
