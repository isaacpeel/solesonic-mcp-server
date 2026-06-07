package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnRosterResponse(List<EspnAthlete> athletes) implements Serializable {}
