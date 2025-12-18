package com.solesonic.mcp.config.tavily;

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

import static com.solesonic.mcp.config.tavily.TavilyConstants.TAVILY_API_WEB_CLIENT;

@Configuration
public class TavilyClientConfig {

    @Value("${tavily.api.uri}")
    private String tavilyApiUri;

    @Value("${tavily.api.key}")
    private String tavilyApiKey;

    @Bean
    @Qualifier(TAVILY_API_WEB_CLIENT)
    public WebClient tavilyApiWebClient(ObjectMapper objectMapper) {
        return WebClient.builder()
                .baseUrl(tavilyApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                    httpHeaders.setBearerAuth(tavilyApiKey);
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }
}
