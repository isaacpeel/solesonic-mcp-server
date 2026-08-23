package com.solesonic.mcp.config.google;

import com.solesonic.mcp.security.google.GoogleRequestAuthorizationFilter;
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

import static com.solesonic.mcp.config.google.GoogleConstants.GOOGLE_API_WEB_CLIENT;

@Configuration
public class GoogleClientConfig {

    @Value("${google.api.uri}")
    private String googleApiUri;

    private final GoogleRequestAuthorizationFilter googleRequestAuthorizationFilter;

    public GoogleClientConfig(GoogleRequestAuthorizationFilter googleRequestAuthorizationFilter) {
        this.googleRequestAuthorizationFilter = googleRequestAuthorizationFilter;
    }

    @Bean
    @Qualifier(GOOGLE_API_WEB_CLIENT)
    public WebClient googleApiWebClient(JsonMapper jsonMapper) {
        return WebClient.builder()
                .baseUrl(googleApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                })
                .filter(googleRequestAuthorizationFilter)
                .build();
    }
}
