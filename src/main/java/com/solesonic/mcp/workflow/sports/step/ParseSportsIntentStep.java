package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptConstants.*;
import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT;

@Component
public class ParseSportsIntentStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "parse-sports-intent";

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentStep.class);

    @Value("classpath:prompt/sports/sports-intent-prompt.st")
    private Resource sportsIntentPromptResource;

    private final ChatClient chatClient;

    public ParseSportsIntentStep(@Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.PARSING_INTENT);
        executionContext.progressTracker().step(name()).update(0.1, "Analyzing your sportsball question");

        String todayDate = todayDate();

        PromptTemplate sportsIntentTemplate = new PromptTemplate(sportsIntentPromptResource);

        Map<String, Object> promptVars = Map.of(
                USER_MESSAGE, context.getOriginalUserMessage(),
                TODAY_DATE, todayDate
        );

        Prompt sportsIntentPrompt = sportsIntentTemplate.create(promptVars);

        SportsQueryIntent sportsQueryIntent = chatClient.prompt(sportsIntentPrompt)
                .call()
                .entity(SportsQueryIntent.class);

        assert sportsQueryIntent != null;
        log.info("Sports intent parse LLM response: {}", sportsQueryIntent.questionTypes());

        log.info("Parsed NBA intent: questionTypes={}, teams={}, players={}",
                sportsQueryIntent.questionTypes(), sportsQueryIntent.teams(),
                sportsQueryIntent.players());

        context.setSportsQueryIntent(sportsQueryIntent);

        executionContext.progressTracker().step(name()).done(
                "Question types: %s, Teams: %s".formatted(
                        sportsQueryIntent.questionTypes(),
                        sportsQueryIntent.hasTeams() ? String.join(", ", sportsQueryIntent.teams()) : "none"
                )
        );
        return WorkflowDecision.continueWorkflow();
    }
}
