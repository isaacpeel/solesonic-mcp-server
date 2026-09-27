package com.solesonic.service.google;

import com.solesonic.mcp.exception.google.GmailException;
import com.solesonic.mcp.exception.google.GoogleReconnectRequiredException;
import com.solesonic.model.google.auth.GoogleTokenExchange;
import com.solesonic.model.google.auth.GoogleTokenResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.solesonic.mcp.config.google.GoogleConstants.GOOGLE_TOKEN_BROKER_WEB_CLIENT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Exchanges a solesonic user id for a short-lived Google access token minted by solesonic-llm-api.
 * The user's Google refresh token never reaches this application.
 * <p>
 * Unlike {@code AtlassianTokenBrokerService} this one caches. Listing an inbox costs one Gmail call
 * per message plus the listing call, and the authorization filter runs on every one of them; without
 * a cache a single tool invocation would hammer the broker a dozen times over for the same token.
 */
@Service
public class GoogleTokenBrokerService {
    private static final Logger log = LoggerFactory.getLogger(GoogleTokenBrokerService.class);

    private static final String CODE = "code";

    /**
     * The broker's error code for "the grant is gone, or was never given". Retrying cannot fix it;
     * the user has to consent again.
     */
    private static final String RECONNECT_REQUIRED = "RECONNECT_REQUIRED";

    /**
     * Seconds shaved off a cached token's lifetime so it is never handed out moments before Google
     * would reject it.
     */
    private static final int EXPIRY_SKEW_SECONDS = 60;

    private final WebClient tokenBrokerWebClient;
    private final JsonMapper jsonMapper;

    private final ConcurrentMap<UUID, GoogleTokenResponse> cachedTokens = new ConcurrentHashMap<>();

    public GoogleTokenBrokerService(@Qualifier(GOOGLE_TOKEN_BROKER_WEB_CLIENT) WebClient tokenBrokerWebClient,
                                    JsonMapper jsonMapper) {
        this.tokenBrokerWebClient = tokenBrokerWebClient;
        this.jsonMapper = jsonMapper;
    }

    public GoogleTokenResponse googleAccessToken(UUID userId) {
        GoogleTokenResponse cachedToken = cachedTokens.get(userId);

        if (cachedToken != null && usable(cachedToken)) {
            log.debug("Reusing cached Google access token for user {}", userId);

            return cachedToken;
        }

        log.info("Exchanging a Google access token for user {}", userId);

        GoogleTokenExchange googleTokenExchange = new GoogleTokenExchange(userId);

        GoogleTokenResponse tokenResponse;

        try {
            tokenResponse = tokenBrokerWebClient.post()
                    .bodyValue(googleTokenExchange)
                    .exchangeToMono(response -> {
                        if (response.statusCode().isError()) {
                            HttpStatusCode statusCode = response.statusCode();

                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> Mono.error(brokerFailure(userId, statusCode, errorBody)));
                        }

                        return response.bodyToMono(GoogleTokenResponse.class);
                    })
                    .block();
        } catch (RuntimeException runtimeException) {
            cachedTokens.remove(userId);

            throw runtimeException;
        }

        if (tokenResponse == null || StringUtils.isBlank(tokenResponse.accessToken())) {
            cachedTokens.remove(userId);

            throw new GmailException("Google token broker returned no access token for user " + userId);
        }

        if (usable(tokenResponse)) {
            cachedTokens.put(userId, tokenResponse);
        }

        return tokenResponse;
    }

    private boolean usable(GoogleTokenResponse tokenResponse) {
        ZonedDateTime issuedAt = tokenResponse.issuedAt();

        if (issuedAt == null) {
            return false;
        }

        return issuedAt.plusSeconds(tokenResponse.expiresInSeconds() - (long) EXPIRY_SKEW_SECONDS)
                .isAfter(ZonedDateTime.now());
    }

    /**
     * Prefers the broker's own error code over the status line: the broker answers a
     * never-connected user with a 400 that means "go consent", which is nothing like a 400 caused by
     * a malformed request.
     */
    private RuntimeException brokerFailure(UUID userId, HttpStatusCode statusCode, String errorBody) {
        String errorCode = errorCode(errorBody);

        if (RECONNECT_REQUIRED.equals(errorCode)
                || (errorCode == null && BAD_REQUEST.equals(statusCode))) {
            log.warn("Google account not connected for user {}", userId);

            return new GoogleReconnectRequiredException("No Google grant for user " + userId);
        }

        log.error("Google token broker failed for user {} with status {}", userId, statusCode);

        return new GmailException(
                "Google token broker failed for user %s with status %s".formatted(userId, statusCode),
                errorBody);
    }

    /**
     * The body is normally the broker's {@code {code, message}} shape, but a rejection from the
     * gateway or the identity provider can arrive as anything at all — so parse defensively rather
     * than binding.
     */
    private String errorCode(String errorBody) {
        if (StringUtils.isBlank(errorBody)) {
            return null;
        }

        try {
            JsonNode root = jsonMapper.readTree(errorBody);
            JsonNode codeNode = root.get(CODE);

            if (codeNode == null || codeNode.isNull()) {
                return null;
            }

            return codeNode.asString();
        } catch (RuntimeException runtimeException) {
            log.debug("Google token broker error body was not JSON: {}", runtimeException.getMessage());

            return null;
        }
    }
}
