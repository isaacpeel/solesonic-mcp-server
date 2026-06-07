package com.solesonic.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnBroadcast(List<String> names) implements Serializable {}
