package com.solesonic.mcp.config.comfyui;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.solesonic.mcp.config.comfyui.ComfyUiConstants.COMFY_UI_WEB_CLIENT;

@Configuration
public class ComfyUiClientConfig {

    @Value("${comfyui.uri:https://comfy.izzy-bot.com}")
    private String comfyUri;

    @Bean
    @Qualifier(COMFY_UI_WEB_CLIENT)
    public WebClient comfyUiWebClient() {
        return WebClient.builder()
                .baseUrl(comfyUri)
                .build();
    }
}
