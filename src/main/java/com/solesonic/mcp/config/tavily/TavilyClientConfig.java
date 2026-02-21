package com.solesonic.mcp.config.tavily;

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

import static com.solesonic.mcp.config.tavily.TavilyConstants.TAVILY_API_WEB_CLIENT;

@Configuration
public class TavilyClientConfig {

    @Value("${tavily.api.uri}")
    private String tavilyApiUri;

    @Value("${tavily.api.key}")
    private String tavilyApiKey;

    @Bean
    @Qualifier(TAVILY_API_WEB_CLIENT)
    public WebClient tavilyApiWebClient(JsonMapper jsonMapper) {
        return WebClient.builder()
                .baseUrl(tavilyApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                    httpHeaders.setBearerAuth(tavilyApiKey);
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                })
                .build();
    }
}
