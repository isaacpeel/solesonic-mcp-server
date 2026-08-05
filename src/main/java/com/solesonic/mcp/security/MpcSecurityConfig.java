package com.solesonic.mcp.security;

import com.solesonic.model.security.SecurityEventReason;
import com.solesonic.service.security.SecurityEventLogger;
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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.solesonic.mcp.api.ResourceMetadataController.WELL_KNOWN_OAUTH_PROTECTED_RESOURCE;
import static com.solesonic.model.security.SecurityEvent.AUTHENTICATION_FAILURE;
import static com.solesonic.model.security.SecurityEvent.AUTHORIZATION_DENIED;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "solesonic.agent.security.enabled", havingValue = "true", matchIfMissing = true)
public class MpcSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(MpcSecurityConfig.class);

    private static final String EXPIRED_MARKER = "expired";
    private static final String SIGNATURE_MARKER = "signature";
    private static final String ISSUER_MARKER = "the iss claim";
    private static final String AUDIENCE_MARKER = "the aud claim";

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
    private final SecurityEventLogger securityEventLogger;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${cors.allowed.origins}")
    private List<String> allowedOrigins;

    @Value("${solesonic.mcp.resource}")
    private String baseResource;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    public MpcSecurityConfig(AuthoritiesService authoritiesService, SecurityEventLogger securityEventLogger) {
        this.authoritiesService = authoritiesService;
        this.securityEventLogger = securityEventLogger;
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
                .authorizeHttpRequests(authz ->
                        authz.requestMatchers(WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                        .requestMatchers(OPTIONS, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, MCP_PREFIX).permitAll()
                        .requestMatchers(AGENT_CARD_POSTFIX).permitAll()
                        .requestMatchers(AGENT_PREFIX).authenticated()
                        .anyRequest().authenticated())
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

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authenticationException) -> {
            securityEventLogger.log(AUTHENTICATION_FAILURE, request, SC_UNAUTHORIZED, reason(authenticationException));

            response.setContentType(APPLICATION_JSON_VALUE);
            response.setStatus(SC_UNAUTHORIZED);
            response.setHeader(WWW_AUTHENTICATE, "\"%s%s\"" .formatted(baseResource, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE));
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, _) -> {
            securityEventLogger.log(AUTHORIZATION_DENIED, request, SC_FORBIDDEN, SecurityEventReason.INSUFFICIENT_AUTHORITY);

            response.sendError(SC_FORBIDDEN, "Access Denied");
        };
    }

    /**
     * Maps the failure to the closed reason enum in one place. The JWT validators cannot see the
     * request, so the entry point reads it back off the exception instead — an
     * {@link OAuth2AuthenticationException} carries an {@code OAuth2Error} whose description
     * distinguishes the cases, which keeps the validators themselves pure.
     */
    private static SecurityEventReason reason(AuthenticationException authenticationException) {
        if (!(authenticationException instanceof OAuth2AuthenticationException oauth2AuthenticationException)) {
            // Nothing decoded a token at all: the request arrived without one.
            return SecurityEventReason.MISSING_TOKEN;
        }

        String description = oauth2AuthenticationException.getError().getDescription();

        if (description == null) {
            return SecurityEventReason.MALFORMED_TOKEN;
        }

        String lowerCaseDescription = description.toLowerCase(Locale.ROOT);

        if (lowerCaseDescription.contains(EXPIRED_MARKER)) {
            return SecurityEventReason.EXPIRED_TOKEN;
        }

        if (lowerCaseDescription.contains(SIGNATURE_MARKER)) {
            return SecurityEventReason.INVALID_SIGNATURE;
        }

        if (lowerCaseDescription.contains(ISSUER_MARKER)) {
            return SecurityEventReason.WRONG_ISSUER;
        }

        if (lowerCaseDescription.contains(AUDIENCE_MARKER)) {
            return SecurityEventReason.WRONG_AUDIENCE;
        }

        return SecurityEventReason.MALFORMED_TOKEN;
    }
}
