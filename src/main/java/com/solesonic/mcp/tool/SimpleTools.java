package com.solesonic.mcp.tool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SimpleTools {

    @Bean
    public ToolCallbackProvider simpleTools(SimpleTools simpleTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(simpleTools)
                .build();
    }
}
