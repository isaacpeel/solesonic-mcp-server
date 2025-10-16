package com.solesonic.mcp.security.atlassian;


import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.model.atlassian.auth.TokenResponse;
import com.solesonic.mcp.service.atlassian.AtlassianTokenBrokerService;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
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

@Component
public class AtlassianRequestAuthorizationFilter implements ExchangeFilterFunction {
    private static final Logger log = LoggerFactory.getLogger(AtlassianRequestAuthorizationFilter.class);
    public static final String BEARER = "Bearer ";

    private final AtlassianTokenBrokerService atlassianTokenBrokerService;

    public AtlassianRequestAuthorizationFilter(AtlassianTokenBrokerService atlassianTokenBrokerService) {
        this.atlassianTokenBrokerService = atlassianTokenBrokerService;
    }

    @Override
    @Nonnull
    public Mono<ClientResponse> filter(ClientRequest request, @NonNull ExchangeFunction next) {
        log.info("Filtering {}: {}", request.method().name(), request.url());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                String userId = jwt.getSubject();

                TokenResponse atlassianAccessToken = atlassianTokenBrokerService.atlassianAccessToken(UUID.fromString(userId));

                log.info("Token received");

                String accessToken = atlassianAccessToken.accessToken();

                if(StringUtils.isBlank(accessToken)) {
                    throw new JiraException("Access Token is `null`");
                }

                ClientRequest authorizedRequest = ClientRequest.from(request)
                        .header(AUTHORIZATION, BEARER + accessToken)
                        .build();

                return next.exchange(authorizedRequest);
            }
        } else {
            log.warn("No authentication found in SecurityContext");
        }

        return next.exchange(request);
    }
}
