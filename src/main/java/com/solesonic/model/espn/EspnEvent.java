package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnEvent(
        String id,
        String date,
        String shortName,
        List<EspnCompetition> competitions,
        EspnStatus status
) implements Serializable {}
