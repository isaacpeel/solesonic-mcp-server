package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnAthletePosition(String abbreviation, String displayName) implements Serializable {}
