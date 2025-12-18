package com.solesonic.mcp.tool;

import org.springaicommunity.mcp.annotation.McpTool;

import java.util.Arrays;
import java.util.stream.Collectors;

public class SolesonicTool {
    /**
     * Generates a dynamic string describing all available tools in the provided classes
     * based on the @McpTool annotation.
     * @param toolClasses One or more classes to inspect for tools
     * @return A formatted string suitable for LLM prompt context.
     */
    public static String availableTools(Class<?>... toolClasses) {
        return Arrays.stream(toolClasses)
                .flatMap(clazz -> Arrays.stream(clazz.getMethods()))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> {
                    McpTool tool = method.getAnnotation(McpTool.class);
                    return "- Tool: `%s`\n  Description: %s".formatted(tool.name(), tool.description().trim());
                })
                .collect(Collectors.joining("\n\n"));
    }
}
