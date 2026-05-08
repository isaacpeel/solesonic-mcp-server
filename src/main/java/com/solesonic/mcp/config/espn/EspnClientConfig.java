package com.solesonic.mcp.config.espn;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.solesonic.mcp.config.espn.EspnConstants.ESPN_API_WEB_CLIENT;

@Configuration
public class EspnClientConfig {

    @Value("${espn.api.uri}")
    private String espnApiUri;

    @Bean
    @Qualifier(ESPN_API_WEB_CLIENT)
    public WebClient espnApiWebClient(JsonMapper jsonMapper) {
        return WebClient.builder()
                .baseUrl(espnApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                })
                .build();
    }
}
