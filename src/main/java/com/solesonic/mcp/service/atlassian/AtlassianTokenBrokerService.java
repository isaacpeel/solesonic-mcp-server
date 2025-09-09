package com.solesonic.mcp.service.atlassian;

import com.solesonic.mcp.model.atlassian.auth.TokenExchange;
import com.solesonic.mcp.model.atlassian.auth.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_TOKEN_BROKER_WEB_CLIENT;

@Service
public class AtlassianTokenBrokerService {
    private static final Logger log = LoggerFactory.getLogger(AtlassianTokenBrokerService.class);
    public static final String ATLASSIAN = "atlassian";
    private final WebClient tokenBrokerWebClient;

    public AtlassianTokenBrokerService(@Qualifier(ATLASSIAN_TOKEN_BROKER_WEB_CLIENT) WebClient tokenBrokerWebClient) {
        this.tokenBrokerWebClient = tokenBrokerWebClient;
    }

    public TokenResponse atlassianAccessToken(UUID userId) {
        log.info("Exchanging an atlassian access token for user {}", userId);

        TokenExchange tokenExchange = new TokenExchange(userId, ATLASSIAN);

        return tokenBrokerWebClient.post()
                .bodyValue(tokenExchange)
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
    }
}
