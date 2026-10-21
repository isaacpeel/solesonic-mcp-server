package com.solesonic.mcp.config.atlassian;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.solesonic.mcp.config.atlassian.AtlassianTokenBrokerClientConfig.ATLASSIAN_TOKEN_BROKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

class AtlassianTokenBrokerClientConfigTest {

    private static final String SERVICE_TOKEN = "service-access-token";

    private HttpServer server;
    private final AtomicReference<String> brokerAuthorization = new AtomicReference<>();
    private final AtomicInteger tokenRequests = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/token", exchange -> {
            tokenRequests.incrementAndGet();
            respond(exchange, """
                    {"access_token":"%s","token_type":"Bearer","expires_in":3600}""".formatted(SERVICE_TOKEN));
        });

        server.createContext("/broker", exchange -> {
            brokerAuthorization.set(exchange.getRequestHeaders().getFirst(AUTHORIZATION));
            respond(exchange, "{}");
        });

        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void brokerRequestFromAThreadWithNoServletRequestCarriesTheServiceToken() {
        WebClient brokerWebClient = brokerWebClient();

        CompletableFuture.runAsync(() -> postToBroker(brokerWebClient), ForkJoinPool.commonPool()).join();

        assertEquals("Bearer " + SERVICE_TOKEN, brokerAuthorization.get());
    }

    @Test
    void serviceTokenIsReusedAcrossBrokerRequests() {
        WebClient brokerWebClient = brokerWebClient();

        postToBroker(brokerWebClient);
        postToBroker(brokerWebClient);

        assertEquals(1, tokenRequests.get());
    }

    @Test
    void missingBrokerRegistrationFailsAtStartup() {
        AtlassianTokenBrokerClientConfig config = new AtlassianTokenBrokerClientConfig();
        InMemoryClientRegistrationRepository otherRegistrationRepository = new InMemoryClientRegistrationRepository(
                registration("some-other-registration"));

        assertThrows(IllegalStateException.class, () -> config.authorizedClientManager(otherRegistrationRepository));
    }

    private WebClient brokerWebClient() {
        AtlassianTokenBrokerClientConfig config = new AtlassianTokenBrokerClientConfig();
        ReflectionTestUtils.setField(config, "atlassianTokenBrokerUrl", baseUrl() + "/broker");

        ReactiveOAuth2AuthorizedClientManager authorizedClientManager = config.authorizedClientManager(
                new InMemoryClientRegistrationRepository(registration(ATLASSIAN_TOKEN_BROKER)));

        return config.atlassianTokenBrokerWebClient(JsonMapper.builder().build(), authorizedClientManager);
    }

    private ClientRegistration registration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(baseUrl() + "/token")
                .build();
    }

    private void postToBroker(WebClient brokerWebClient) {
        brokerWebClient.post()
                .bodyValue(Map.of("userId", "7d0f7a0e-4a8f-4b83-9a55-0f2f7c3c2b11"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private String baseUrl() {
        return "http://%s:%d".formatted(InetAddress.getLoopbackAddress().getHostAddress(), server.getAddress().getPort());
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
