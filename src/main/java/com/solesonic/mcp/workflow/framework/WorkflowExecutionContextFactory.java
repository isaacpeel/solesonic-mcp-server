package com.solesonic.mcp.workflow.framework;

import com.solesonic.mcp.workflow.ProgressReporter;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class WorkflowExecutionContextFactory {
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CHAT_ID_KEY = "chatId";

    public WorkflowExecutionContext create(
            McpAsyncRequestContext mcpAsyncRequestContext,
            String workflowName,
            Map<String, Double> stepWeights
    ) {
        ProgressReporter progressReporter = new ProgressReporter(mcpAsyncRequestContext);
        WorkflowNotificationService notificationService = new LoggingWorkflowNotificationService(progressReporter);
        Map<String, Object> requestMetadata = mcpAsyncRequestContext.requestMeta();
        String correlationId = resolveCorrelationId(requestMetadata);

        return new WorkflowExecutionContext(
                workflowName,
                correlationId,
                notificationService,
                stepWeights,
                requestMetadata
        );
    }

    private String resolveCorrelationId(Map<String, Object> requestMetadata) {
        if (requestMetadata == null || requestMetadata.isEmpty()) {
            return UUID.randomUUID().toString();
        }

        if (requestMetadata.containsKey(CORRELATION_ID_KEY)) {
            return requestMetadata.get(CORRELATION_ID_KEY).toString();
        }

        if (requestMetadata.containsKey(CHAT_ID_KEY)) {
            return requestMetadata.get(CHAT_ID_KEY).toString();
        }

        return UUID.randomUUID().toString();
    }
}
