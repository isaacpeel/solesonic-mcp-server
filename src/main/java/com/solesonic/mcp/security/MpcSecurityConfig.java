package com.solesonic.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class MpcSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(MpcSecurityConfig.class);

    @SuppressWarnings("unused")
    public static final String SCOPE_MCP_INVOKE = "SCOPE_MCP_INVOKE";
    public static final String SCOPE_ = "SCOPE_";
    public static final String SCOPE = "scope";

    public static final String GROUP = "GROUP_";
    public static final String GROUPS = "groups";

    public static final String ROLE = "ROLE_";
    public static final String ROLES = "roles";


    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix(SCOPE_);
        authoritiesConverter.setAuthoritiesClaimName(SCOPE);

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> grantedAuthorities = authoritiesConverter.convert(jwt);
            Collection<GrantedAuthority> groupAuthorities = extractGroupAuthorities(jwt);
            Collection<GrantedAuthority> roleAuthorities = extractRoleAuthorities(jwt);

            return Stream.of(grantedAuthorities, groupAuthorities, roleAuthorities)
                    .flatMap(Collection::stream)
                    .toList();
        });

        return jwtAuthenticationConverter;
    }

    private Collection<GrantedAuthority> extractGroupAuthorities(Jwt jwt) {
        Object groupsClaim = jwt.getClaim(GROUPS);


        if (groupsClaim instanceof List<?> groups) {
            return groups.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(group -> new SimpleGrantedAuthority(GROUP + group.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();

        }

        return List.of();
    }

    private Collection<GrantedAuthority> extractRoleAuthorities(Jwt jwt) {
        Object rolesClaim = jwt.getClaim(ROLES);

        if (rolesClaim instanceof List<?> roles) {
            return  roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority(ROLE + role.toUpperCase()))
                    .map(GrantedAuthority.class::cast)
                    .toList();

        }

        return List.of();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.exceptionHandling(config -> config.accessDeniedHandler(accessDeniedHandler()));
        http.exceptionHandling(config -> config.authenticationEntryPoint(authenticationEntryPoint()));

        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        return http.build();
    }


    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, _) -> {
            log.warn("{} - Unauthorized access attempt: remote addr: {} {} - {}", SC_UNAUTHORIZED, request.getRemoteAddr(), request.getMethod(), request.getRequestURI());

            response.setContentType(APPLICATION_JSON_VALUE);
            response.setStatus(SC_UNAUTHORIZED);
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, _) -> {
            log.warn("{} - Access Denied: {} trying to access {} from IP: {}", SC_FORBIDDEN, request.getRemoteAddr(), request.getRequestURI(), request.getRemoteAddr());
            response.sendError(SC_FORBIDDEN, "Access Denied");
        };
    }
}
