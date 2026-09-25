package com.solesonic.mcp.security.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.security.identity.CallerIdentity;
import com.solesonic.model.google.auth.GoogleTokenResponse;
import com.solesonic.service.google.GoogleTokenBrokerService;
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
 * Puts the calling user's brokered Google access token on every Gmail request.
 * <p>
 * The caller is read from the request's {@link CallerIdentity} attribute, never from ambient
 * thread state — this filter runs wherever the request is subscribed, which is not the servlet
 * thread. A request without a caller fails here rather than going out unauthenticated.
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
        return Mono.defer(() -> {
            log.info("Filtering {}: {}", request.method().name(), request.url());

            CallerIdentity callerIdentity = CallerIdentity.from(request).orElse(null);

            if (callerIdentity == null) {
                return Mono.error(new GmailException("No caller identity attached to Google request %s %s"
                        .formatted(request.method().name(), request.url().getPath())));
            }

            GoogleTokenResponse googleTokenResponse = googleTokenBrokerService.googleAccessToken(callerIdentity.userId());

            log.debug("Google Token received.");

            String accessToken = googleTokenResponse.accessToken();

            if (StringUtils.isEmpty(accessToken)) {
                return Mono.error(new GmailException("Google access token is `null`"));
            }

            ClientRequest authorizedRequest = ClientRequest.from(request)
                    .header(AUTHORIZATION, BEARER + accessToken)
                    .build();

            return next.exchange(authorizedRequest);
        });
    }
}
