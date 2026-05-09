package com.solesonic.mcp.a2a;

import com.solesonic.mcp.workflow.SportsResearchWorkflow;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SportsAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(SportsAgentExecutor.class);

    private final SportsResearchWorkflow sportsResearchWorkflow;

    public SportsAgentExecutor(SportsResearchWorkflow sportsResearchWorkflow) {
        this.sportsResearchWorkflow = sportsResearchWorkflow;
    }

    @Override
    public void execute(RequestContext context, EventQueue queue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, queue);
        if (context.getTask() == null) updater.submit();
        updater.startWork();

        String userMessage = extractText(context);
        log.info("NBA research agent invoked: userMessage={}", userMessage);

        try {
            SportsResearchWorkflowContext workflowContext =
                    sportsResearchWorkflow.startWorkflow(userMessage);

            String analysis = workflowContext.getFinalAnalysis();
            if (analysis == null || analysis.isBlank()) {
                analysis = "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";
            }

            updater.addArtifact(List.of(new TextPart(analysis)), null, null, null);
            updater.complete();
        } catch (Exception exception) {
            log.error("NBA research agent failed: userMessage={}", userMessage, exception);
            updater.fail();
        }
    }

    @Override
    public void cancel(RequestContext context, EventQueue queue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, queue);
        updater.cancel();
    }

    private static String extractText(RequestContext context) {
        return context.getMessage()
                .getParts()
                .stream()
                .filter(part -> part instanceof TextPart)
                .map(part -> ((TextPart) part).getText())
                .findFirst()
                .orElse("");
    }
}
