package com.solesonic.mcp.config.comfyui;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.COMFY_UI_WEB_CLIENT;

@Configuration
public class ComfyUiClientConfig {

    /**
     * A single 1024x1024 PNG lands around 1.5MB, but larger supported sizes leave the global
     * spring.http.codecs.max-in-memory-size=10MB with very little headroom, and overflowing it
     * surfaces as an opaque buffer-limit failure mid-download.
     */
    private static final int MAX_IN_MEMORY_SIZE = 32 * 1024 * 1024;

    @Value("${comfyui.api.uri}")
    private String comfyUiApiUri;

    @Value("${comfyui.api.response-timeout-seconds}")
    private int responseTimeoutSeconds;

    @Bean
    @Qualifier(COMFY_UI_WEB_CLIENT)
    public WebClient comfyUiWebClient(JsonMapper jsonMapper) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

        return WebClient.builder()
                .baseUrl(comfyUiApiUri)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeaders(httpHeaders -> httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                    configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE);
                })
                .build();
    }
}
