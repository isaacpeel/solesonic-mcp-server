package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnBroadcast(List<String> names) implements Serializable {}
