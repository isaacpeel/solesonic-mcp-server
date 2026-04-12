package com.solesonic.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.solesonic.mcp.api.ResourceMetadataController.WELL_KNOWN_OAUTH_PROTECTED_RESOURCE;
import static org.springframework.http.HttpMethod.*;

@SuppressWarnings("unused")
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
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

    private final AuthoritiesService authoritiesService;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${cors.allowed.origins}")
    private List<String> allowedOrigins;

    @Value("${solesonic.mcp.resource}")
    private String baseResource;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    public MpcSecurityConfig(AuthoritiesService authoritiesService) {
        this.authoritiesService = authoritiesService;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix(SCOPE_);
        scopeConverter.setAuthoritiesClaimName(SCOPE);

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
            Collection<GrantedAuthority> groupAuthorities = authoritiesService.extractGroupAuthorities(jwt);
            Collection<GrantedAuthority> roleAuthorities = authoritiesService.extractRoleAuthorities(jwt);

            roleAuthorities.forEach(authority -> log.debug(authority.getAuthority()));

            return Flux.fromIterable(
                    Stream.of(scopeAuthorities, groupAuthorities, roleAuthorities)
                            .flatMap(Collection::stream)
                            .toList()
            );
        });

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of(GET.name(), OPTIONS.name(), POST.name()));
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .exceptionHandling(config -> config
                        .accessDeniedHandler(accessDeniedHandler())
                        .authenticationEntryPoint(authenticationEntryPoint())
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authz -> authz
                        .pathMatchers(WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/mcp/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    private ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, exception) -> {
            log.warn("{} - Unauthorized access attempt: {} {}",
                    HttpStatus.UNAUTHORIZED.value(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set(WWW_AUTHENTICATE,
                    "\"%s%s\"".formatted(baseResource, WELL_KNOWN_OAUTH_PROTECTED_RESOURCE));
            return exchange.getResponse().setComplete();
        };
    }

    private ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, exception) -> {
            log.warn("{} - Access Denied: {} trying to access {}",
                    HttpStatus.FORBIDDEN.value(),
                    exchange.getRequest().getRemoteAddress(),
                    exchange.getRequest().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        };
    }
}
