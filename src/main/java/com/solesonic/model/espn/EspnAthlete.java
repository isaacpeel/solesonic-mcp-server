package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnAthlete(
        String fullName,
        String jersey,
        EspnAthletePosition position
) implements Serializable {}
