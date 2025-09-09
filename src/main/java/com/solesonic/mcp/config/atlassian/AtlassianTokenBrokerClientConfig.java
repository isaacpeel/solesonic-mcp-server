package com.solesonic.mcp.config.atlassian;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_TOKEN_BROKER_WEB_CLIENT;

@Configuration
public class AtlassianTokenBrokerClientConfig {
    @Value("${atlassian.token.broker.uri}")
    private String atlassianTokenBrokerUrl;

    @Bean
    @Qualifier(ATLASSIAN_TOKEN_BROKER_WEB_CLIENT)
    public WebClient atlassianTokenBrokerWebClient(ObjectMapper objectMapper) {

        return WebClient.builder()
                .baseUrl(atlassianTokenBrokerUrl)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }


}
