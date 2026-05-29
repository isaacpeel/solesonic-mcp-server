package com.solesonic.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    public String weatherLookup(String city) {
        log.info("Received request for weather in {}", city);

        return "The weather in " + city + " is sunny.";
    }
}