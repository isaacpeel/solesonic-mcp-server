package com.solesonic.mcp.security.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.model.google.auth.GoogleTokenResponse;
import com.solesonic.service.google.GoogleTokenBrokerService;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Puts the calling user's brokered Google access token on every Gmail request.
 * <p>
 * Reading the caller's JWT off {@link SecurityContextHolder} works only because
 * {@code MpcSecurityConfig} sets {@code MODE_INHERITABLETHREADLOCAL} — this filter runs wherever the
 * request is subscribed, which is not the servlet thread.
 */
@Component
public class GoogleRequestAuthorizationFilter implements ExchangeFilterFunction {
    private static final Logger log = LoggerFactory.getLogger(GoogleRequestAuthorizationFilter.class);
    public static final String BEARER = "Bearer ";

    private final GoogleTokenBrokerService googleTokenBrokerService;

    public GoogleRequestAuthorizationFilter(GoogleTokenBrokerService googleTokenBrokerService) {
        this.googleTokenBrokerService = googleTokenBrokerService;
    }

    @Override
    @Nonnull
    public Mono<ClientResponse> filter(@Nonnull ClientRequest request, @Nonnull ExchangeFunction next) {
        log.info("Filtering {}: {}", request.method().name(), request.url());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                String userId = jwt.getSubject();

                GoogleTokenResponse googleTokenResponse = googleTokenBrokerService.googleAccessToken(UUID.fromString(userId));

                log.debug("Google Token received.");

                String accessToken = googleTokenResponse.accessToken();

                if (StringUtils.isEmpty(accessToken)) {
                    throw new GmailException("Google access token is `null`");
                }

                ClientRequest authorizedRequest = ClientRequest.from(request)
                        .header(AUTHORIZATION, BEARER + googleTokenResponse.accessToken())
                        .build();

               return next.exchange(authorizedRequest);
            }
        } else {
            log.warn("No authentication found in SecurityContext");
        }

        return next.exchange(request);
    }
}
