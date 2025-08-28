package com.solesonic.mcp.service;

import org.slf4j.Logger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WeatherService.class);

    @SuppressWarnings("unused")
    @Tool(description = "Returns the weather in the given city.", name = "weather_lookup")
    @PreAuthorize("hasAuthority('GROUP_MCP-GET-WEATHER')")
    public String weatherLookup(String city, ToolContext toolContext) {
        log.info("Received request for weather in {}", city);


        return "The weather in " + city + " is sunny.";
    }
}