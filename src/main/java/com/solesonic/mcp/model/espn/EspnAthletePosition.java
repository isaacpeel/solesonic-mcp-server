package com.solesonic.mcp.model.espn;

import java.io.Serializable;

public record EspnAthletePosition(String abbreviation, String displayName) implements Serializable {}
