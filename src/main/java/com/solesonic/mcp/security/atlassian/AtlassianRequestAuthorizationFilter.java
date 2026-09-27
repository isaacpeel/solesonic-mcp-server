package com.solesonic.mcp.security.atlassian;


import com.solesonic.mcp.exception.atlassian.JiraException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.atlassian.auth.TokenResponse;
import com.solesonic.service.atlassian.AtlassianTokenBrokerService;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Puts the calling user's brokered Atlassian access token on every Atlassian API request.
 * <p>
 * The caller is read from the request's {@link CallerIdentity} attribute, never from ambient
 * thread state: these requests are routinely issued from graph and fan-out threads that carry no
 * security context. A request without a caller fails here rather than going out unauthenticated.
 */
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
    public Mono<ClientResponse> filter(@Nonnull ClientRequest request, @Nonnull ExchangeFunction next) {
        return Mono.defer(() -> {
            log.info("Filtering {}: {}", request.method().name(), request.url());

            CallerIdentity callerIdentity = CallerIdentity.from(request).orElse(null);

            if (callerIdentity == null) {
                return Mono.error(new JiraException("No caller identity attached to Atlassian request %s %s"
                        .formatted(request.method().name(), request.url().getPath())));
            }

            TokenResponse atlassianAccessToken = atlassianTokenBrokerService.atlassianAccessToken(callerIdentity.userId());

            log.debug("Jira Token received");

            String accessToken = atlassianAccessToken.accessToken();

            if (StringUtils.isBlank(accessToken)) {
                return Mono.error(new JiraException("Jira Access Token is `null`"));
            }

            ClientRequest authorizedRequest = ClientRequest.from(request)
                    .header(AUTHORIZATION, BEARER + accessToken)
                    .build();

            return next.exchange(authorizedRequest);
        });
    }
}
