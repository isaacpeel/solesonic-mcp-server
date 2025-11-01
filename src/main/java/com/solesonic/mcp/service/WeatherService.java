package com.solesonic.mcp.service;

import org.slf4j.Logger;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WeatherService.class);

    @SuppressWarnings("unused")
    @McpTool(description = "Returns the weather in the given city.", name = "weather_lookup")
    @PreAuthorize("hasAuthority('ROLE_MCP-GET-WEATHER')")
    public String weatherLookup(String city) {
        log.info("Received request for weather in {}", city);

        return "The weather in " + city + " is sunny.";
    }
}