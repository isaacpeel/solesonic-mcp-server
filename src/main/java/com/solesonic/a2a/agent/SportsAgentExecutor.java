package com.solesonic.a2a.agent;

import com.solesonic.a2a.workflow.sports.SportsState;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component("nba")
public class SportsAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(SportsAgentExecutor.class);

    public static final String PROGRESS_CALLBACK_KEY = "progressCallback";

    private static final String FALLBACK_ANALYSIS =
            "Unable to find information for your NBA question. Please try rephrasing or check NBA.com directly.";

    private final CompiledGraph<SportsState> sportsResearchGraph;

    public SportsAgentExecutor(CompiledGraph<SportsState> sportsResearchGraph) {
        this.sportsResearchGraph = sportsResearchGraph;
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

        Consumer<String> progressCallback = message -> {
            Message statusMessage = taskUpdater.newAgentMessage(List.of(new TextPart(message)), null);
            taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
        };

        Map<String, Object> input = Map.of(SportsState.USER_MESSAGE, userMessage);

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addMetadata(PROGRESS_CALLBACK_KEY, progressCallback)
                .build();
        AtomicReference<SportsState> finalStateRef = new AtomicReference<>();

        try {
            sportsResearchGraph.stream(input, runnableConfig)
                    .forEachAsync(output -> {
                        finalStateRef.set(output.state());

                        if (!output.isEND()) {
                            Message statusMessage = taskUpdater.newAgentMessage(
                                    List.of(new TextPart("Completed: " + output.node())), null);
                            taskUpdater.updateStatus(TaskState.WORKING, statusMessage);
                        }
                    })
                    .join();

            SportsState finalState = finalStateRef.get();
            String analysis = finalState != null
                    ? finalState.finalAnalysis().orElse(FALLBACK_ANALYSIS)
                    : FALLBACK_ANALYSIS;

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
