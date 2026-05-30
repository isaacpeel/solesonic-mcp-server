package com.solesonic.mcp.model.espn;

import java.io.Serializable;
import java.util.List;

public record EspnScheduleResponse(List<EspnEvent> events) implements Serializable {}
