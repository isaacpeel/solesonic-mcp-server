package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnConferenceStandings(List<EspnStandingsEntry> entries) implements Serializable {}
