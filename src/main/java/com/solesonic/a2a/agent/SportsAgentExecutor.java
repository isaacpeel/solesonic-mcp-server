package com.solesonic.a2a.agent;

import com.solesonic.a2a.workflow.SportsResearchWorkflow;
import com.solesonic.a2a.workflow.sports.SportsResearchWorkflowContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("nba-sports-ball")
public class SportsAgentExecutor implements AgentExecutor {
    private static final Logger log = LoggerFactory.getLogger(SportsAgentExecutor.class);

    private final SportsResearchWorkflow sportsResearchWorkflow;

    public SportsAgentExecutor(SportsResearchWorkflow sportsResearchWorkflow) {
        this.sportsResearchWorkflow = sportsResearchWorkflow;
    }

    @Override
    public void execute(RequestContext requestContext, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater taskUpdater = new TaskUpdater(requestContext, eventQueue);

        if (requestContext.getTask() == null) {
            taskUpdater.submit();
        }

        taskUpdater.startWork();

        String userMessage = extractText(requestContext);
        log.info("NBA research agent invoked: userMessage={}", userMessage);

        try {
            SportsResearchWorkflowContext sportsResearchWorkflowContext =
                    sportsResearchWorkflow.startWorkflow(userMessage, (_, progressMessage) -> {

                        Message statusMessage = taskUpdater.newAgentMessage(
                                List.of(new TextPart(progressMessage)), null);

                        taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                    });

            String analysis = sportsResearchWorkflowContext.getFinalAnalysis();

            if (StringUtils.isEmpty(analysis)) {
                analysis = "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";
            }

            taskUpdater.addArtifact(List.of(new TextPart(analysis)), null, null, null);
            taskUpdater.complete();
        } catch (Exception exception) {
            log.error("NBA research agent failed: userMessage={}", userMessage, exception);
            taskUpdater.fail();
        }
    }

    @Override
    public void cancel(RequestContext context, EventQueue queue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, queue);
        updater.cancel();
    }

    private static String extractText(RequestContext requestContext) {
        return requestContext.getMessage()
                .getParts()
                .stream()
                .filter(part -> part instanceof TextPart)
                .map(part -> ((TextPart) part).getText())
                .findFirst()
                .orElse("");
    }
}
