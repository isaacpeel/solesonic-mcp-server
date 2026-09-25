package com.solesonic.mcp.config.atlassian;

import com.solesonic.mcp.security.atlassian.AtlassianRequestAuthorizationFilter;
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

import static com.solesonic.mcp.config.atlassian.AtlassianConstants.ATLASSIAN_API_WEB_CLIENT;

@Configuration
public class AtlassianClientConfig {
    @Value("${jira.api.uri}")
    private String atlassianApiUri;

    private final AtlassianRequestAuthorizationFilter atlassianRequestAuthorizationFilter;
    private final AtlassianErrorResponseFilter atlassianErrorResponseFilter;

    public AtlassianClientConfig(AtlassianRequestAuthorizationFilter atlassianRequestAuthorizationFilter,
                                 AtlassianErrorResponseFilter atlassianErrorResponseFilter) {
        this.atlassianRequestAuthorizationFilter = atlassianRequestAuthorizationFilter;
        this.atlassianErrorResponseFilter = atlassianErrorResponseFilter;
    }

    @Bean
    @Qualifier(ATLASSIAN_API_WEB_CLIENT)
    public WebClient atlassianApiWebClient(JsonMapper jsonMapper) {

        return WebClient.builder()
                .baseUrl(atlassianApiUri)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                    httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));

                })
                .filter(atlassianRequestAuthorizationFilter)
                .filter(atlassianErrorResponseFilter)
                .build();
    }
}
