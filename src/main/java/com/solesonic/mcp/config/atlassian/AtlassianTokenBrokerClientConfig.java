package com.solesonic.mcp.config.atlassian;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_TOKEN_BROKER_WEB_CLIENT;

/**
 * The client this server uses to authenticate <em>itself</em> to the Atlassian token broker.
 * <p>
 * The {@code client_credentials} token is obtained through the reactive OAuth2 client rather than
 * the servlet one. The servlet filter only attaches a token when the current thread carries an
 * {@code HttpServletRequest} and {@code HttpServletResponse}; broker calls are made from graph and
 * fan-out threads that carry neither, and in that case it silently sends the request without any
 * {@code Authorization} header. The reactive filter has no such dependency.
 */
@Configuration
public class AtlassianTokenBrokerClientConfig {
    public static final String ATLASSIAN_TOKEN_BROKER = "atlassian-token-broker";

    @Value("${atlassian.token.broker.uri}")
    private String atlassianTokenBrokerUrl;

    @Bean
    @Qualifier(ATLASSIAN_TOKEN_BROKER_WEB_CLIENT)
    public WebClient atlassianTokenBrokerWebClient(JsonMapper jsonMapper,
                                                   ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId(ATLASSIAN_TOKEN_BROKER);

        return WebClient.builder()
                .baseUrl(atlassianTokenBrokerUrl)
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

    /**
     * Shared by the Atlassian and Google broker clients. The token belongs to this service rather
     * than to any user, so it is held once, in memory, and renewed by the client-credentials
     * provider shortly before it expires.
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {
        ClientRegistration brokerRegistration = clientRegistrationRepository.findByRegistrationId(ATLASSIAN_TOKEN_BROKER);

        if (brokerRegistration == null) {
            throw new IllegalStateException("No OAuth2 client registration named '%s' is configured"
                    .formatted(ATLASSIAN_TOKEN_BROKER));
        }

        InMemoryReactiveClientRegistrationRepository reactiveClientRegistrationRepository =
                new InMemoryReactiveClientRegistrationRepository(brokerRegistration);

        InMemoryReactiveOAuth2AuthorizedClientService authorizedClientService =
                new InMemoryReactiveOAuth2AuthorizedClientService(reactiveClientRegistrationRepository);

        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        reactiveClientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }
}
