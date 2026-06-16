package com.solesonic.model.espn;

import java.io.Serializable;

public record EspnConference(String name, String abbreviation, EspnConferenceStandings standings) implements Serializable {}
