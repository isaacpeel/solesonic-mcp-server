package com.solesonic.mcp.service;

import org.slf4j.Logger;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WeatherService.class);

    @SuppressWarnings("unused")
    @Tool(description = "Returns the weather in the given city.", name = "weather_lookup")
    public String weatherLookup(String city, ToolContext toolContext) {
        log.info("Received request for weather in {}", city);

        log.info("toolContext: {}", toolContext);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String caller = "unknown";

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            String sub = jwt.getSubject();
            caller = preferredUsername != null ? preferredUsername : (email != null ? email : sub);
        }

        return "The weather in " + city + " is sunny. Requested by: " + caller;
    }
}