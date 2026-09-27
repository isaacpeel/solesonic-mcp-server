package com.solesonic.mcp.config.google;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.solesonic.mcp.config.atlassian.AtlassianTokenBrokerClientConfig.ATLASSIAN_TOKEN_BROKER;
import static com.solesonic.mcp.config.google.GoogleConstants.GOOGLE_TOKEN_BROKER_WEB_CLIENT;

/**
 * The client this server uses to authenticate <em>itself</em> to the Google token broker before
 * asking it for a user's Google token.
 * <p>
 * Two deliberate reuses of the Atlassian configuration:
 * <ul>
 *   <li>the {@code client_credentials} registration is shared. There is one MCP service client in
 *       the identity provider, and it fronts both broker endpoints; splitting it would mean a second
 *       client plus a second pair of credentials for no isolation that matters here. The service
 *       account needs the {@code token-mint-gmail} role in addition to {@code token-mint-jira}.</li>
 *   <li>{@link OAuth2AuthorizedClientManager} arrives as a bean-method parameter. That bean is
 *       declared, unqualified, by {@code AtlassianTokenBrokerClientConfig}; declaring a second one
 *       here would leave Spring with two candidates and no way to choose.</li>
 * </ul>
 */
@Configuration
public class GoogleTokenBrokerClientConfig {

    @Value("${google.token.broker.uri}")
    private String googleTokenBrokerUri;

    @Bean
    @Qualifier(GOOGLE_TOKEN_BROKER_WEB_CLIENT)
    public WebClient googleTokenBrokerWebClient(JsonMapper jsonMapper,
                                                OAuth2AuthorizedClientManager authorizedClientManager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId(ATLASSIAN_TOKEN_BROKER);

        return WebClient.builder()
                .baseUrl(googleTokenBrokerUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                })
                .filter(oauth2Client)
                .build();
    }
}
