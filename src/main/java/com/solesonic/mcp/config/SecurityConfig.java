package com.solesonic.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // For machine-to-machine APIs, CSRF can be disabled
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Allow health and info without authentication
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // All other endpoints require the mcp.invoke scope
                .anyRequest().hasAuthority("SCOPE_mcp.invoke")
            )
            // Configure as a JWT resource server; JWKS/issuer configured via properties
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
