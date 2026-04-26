package com.solesonic.mcp.prompt;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptUtil.buildPromptResult;

@SuppressWarnings("unused")
@Service
public class TaskPromptProvider {
    private static final Logger log = LoggerFactory.getLogger(TaskPromptProvider.class);

    private static final String USER_MESSAGE = "input";
    private static final String TASK_TOOL = "task_tool";

    @Value("classpath:prompt/task-prompt.st")
    private Resource taskPrompt;

    @McpPrompt(
            name = "task-prompt",
            title = "Task Prompt",
            description = "A task execution prompt")
    public McpSchema.GetPromptResult basicPrompt(@McpArg(name = "userMessage", description = "A message used as input to a task.") String userMessage,
                                                 @McpArg(name = "taskTool", description = "The name of the task to execute") String taskTool) {
        log.info("Getting task prompt.");

        Map<String, Object> promptContext = Map.of(
                USER_MESSAGE, userMessage,
                TASK_TOOL, taskTool
        );

        return buildPromptResult("task-prompt", this.taskPrompt, promptContext);
    }
}
