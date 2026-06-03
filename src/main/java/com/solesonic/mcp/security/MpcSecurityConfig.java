package com.solesonic.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.solesonic.mcp.api.ResourceMetadataController.WELL_KNOWN_OAUTH_PROTECTED_RESOURCE;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SuppressWarnings("unused")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "solesonic.agent.security.enabled", havingValue = "true", matchIfMissing = true)
public class MpcSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(MpcSecurityConfig.class);

    @SuppressWarnings("unused")
    public static final String SCOPE_MCP_INVOKE = "SCOPE_MCP_INVOKE";
    public static final String SCOPE_ = "SCOPE_";
    public static final String SCOPE = "scope";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization",
            "WWW-Authenticate",
            "Content-Type",
            "Cache-Control",
            "Expires",
            "mcp-protocol-version",
            "Mcp-Session-Id"
    );
    public static final String OPENID = "openid";
    public static final String PROFILE = "profile";
    public static final String EMAIL = "email";
    public static final String MCP_PREFIX = "/mcp/**";
    public static final String AGENT_CARD_POSTFIX = "/.well-known/agent-card.json";
    public static final String AGENT_PREFIX = "/a2a/**";

    private final AuthoritiesService authoritiesService;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${cors.allowed.origins}")
    private List<String> allowedOrigins;

    @Value("${solesonic.mcp.resource}")
    private String baseResource;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${solesonic.mcp.resource}")
    @SuppressWarnings("unused")
    private String clientRegistrationResource;

    public MpcSecurityConfig(AuthoritiesService authoritiesService) {
        this.authoritiesService = authoritiesService;
    }

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
            Collection<GrantedAuthority> groupAuthorities = authoritiesService.extractGroupAuthorities(jwt);
            Collection<GrantedAuthority> roleAuthorities = authoritiesService.extractRoleAuthorities(jwt);

            roleAuthorities.forEach(grantedAuthority -> log.debug(grantedAuthority.getAuthority()));

            return Stream.of(grantedAuthorities, groupAuthorities, roleAuthorities)
                    .flatMap(Collection::stream)
                    .toList();
        });

        return jwtAuthenticationConverter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of(GET.name(), OPTIONS.name(), POST.name()));

        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(ALLOWED_HEADERS);

        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", configuration);

        return urlBasedCorsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .exceptionHandling(config -> config
                        .accessDeniedHandler(accessDeniedHandler())
                        .authenticationEntryPoint(authenticationEntryPoint())
                )
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> {
                    authz.requestMatchers(WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                            .requestMatchers(OPTIONS, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, MCP_PREFIX).permitAll()
                            .requestMatchers(AGENT_CARD_POSTFIX).permitAll()
                            .requestMatchers(AGENT_PREFIX).authenticated()
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                        .protectedResourceMetadata(metadata -> metadata
                                .protectedResourceMetadataCustomizer(builder -> builder
                                        .authorizationServer(issuerUri)
                                        .scope(OPENID)
                                        .scope(PROFILE)
                                        .scope(EMAIL)
                                )
                        )
                );

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        return http.build();
    }

    private String resolveRemoteHost(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, _) -> {
            String resolveRemoteHost = resolveRemoteHost(request);
            String method = request.getMethod();
            String requestURI = request.getRequestURI();

            log.warn("{} - Unauthorized access attempt: remote host: {} {} - {}", SC_UNAUTHORIZED, resolveRemoteHost, method, requestURI);

            response.setContentType(APPLICATION_JSON_VALUE);
            response.setStatus(SC_UNAUTHORIZED);
            response.setHeader(WWW_AUTHENTICATE, "\"%s%s\"" .formatted(baseResource, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE));
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, _) -> {
            String resolveRemoteHost = resolveRemoteHost(request);
            String method = request.getMethod();
            String requestURI = request.getRequestURI();

            log.warn("{} - Access Denied: {} {} - {}", SC_FORBIDDEN, resolveRemoteHost, method, requestURI);
            response.sendError(SC_FORBIDDEN, "Access Denied");
        };
    }
}
