package com.solesonic.mcp.tool;

import com.solesonic.mcp.service.SimpleService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class SimpleTools {

    @Bean
    public ToolCallbackProvider simpleMcpTools(SimpleService simpleService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(simpleService)
                .build();
    }
}
