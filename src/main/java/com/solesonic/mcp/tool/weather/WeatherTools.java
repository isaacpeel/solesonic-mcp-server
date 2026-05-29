package com.solesonic.mcp.tool.weather;

import com.solesonic.mcp.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@SuppressWarnings("unused")
@Service
public class WeatherTools {
    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @McpTool(description = "Returns the weather in the given city.", name = "weather_lookup")
    @PreAuthorize("hasAuthority('ROLE_MCP-GET-WEATHER')")
    public String weatherLookup(String city) {
        log.info("Received tool request for weather in {}", city);

        return weatherService.weatherLookup(city);
    }
}
