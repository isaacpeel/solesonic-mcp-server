package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnStandingsResponse(List<EspnConference> children) implements Serializable {}
