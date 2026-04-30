package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.SportsWorkflowStage;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solesonic.mcp.workflow.sports.SportsChatClientFactory;
import com.solesonic.mcp.workflow.sports.SportsChatProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;

@Component
public class ParseSportsIntentStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "parse-sports-intent";

    private static final Logger log = LoggerFactory.getLogger(ParseSportsIntentStep.class);
    public static final String ANALYZING_YOUR_NBA_QUESTION = "Analyzing your NBA question";

    @Value("classpath:prompt/sports/sports-intent-prompt.st")
    private Resource sportsIntentPrompt;

    private final SportsChatClientFactory chatClientFactory;

    public ParseSportsIntentStep(SportsChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.PARSING_INTENT);

        executionContext.progressTracker().step(name()).update(0.1, ANALYZING_YOUR_NBA_QUESTION);

        PromptTemplate sportsIntentTemplate = new PromptTemplate(sportsIntentPrompt);

        Map<String, Object> promptVars = Map.of(USER_MESSAGE, context.getOriginalUserMessage());

        Prompt sportsIntentPrompt = sportsIntentTemplate.create(promptVars);

        try {
            ChatClient chatClient = chatClientFactory.forProfile(SportsChatProfile.INTENT_PARSE);
            SportsQueryIntent sportsQueryIntent = chatClient.prompt(sportsIntentPrompt)
                    .call()
                    .entity(SportsQueryIntent.class);

            assert sportsQueryIntent != null;

            log.info("Parsed sports intent: questionTypes={}, teams={}, players={}, focusPlayer={}",
                    sportsQueryIntent.questionTypes(),
                    sportsQueryIntent.teams(),
                    sportsQueryIntent.players(),
                    sportsQueryIntent.focusPlayer());

            context.setSportsQueryIntent(sportsQueryIntent);

            String progressNotification = "Question types: %s, Teams: %s%s".formatted(
                    String.join(", ", sportsQueryIntent.questionTypes()),
                    sportsQueryIntent.hasTeams() ? String.join(", ", sportsQueryIntent.teams()) : "none",
                    sportsQueryIntent.hasFocusPlayer() ? ", Focus player: " + sportsQueryIntent.focusPlayer() : ""
            );

            executionContext.progressTracker()
                    .step(name())
                    .done(progressNotification);

            return WorkflowDecision.continueWorkflow();
        } catch (Exception exception) {
            log.error("Failed to parse sports intent from LLM response", exception);
            return WorkflowDecision.failed("Could not parse sports question intent: " + exception.getMessage());
        }
    }
}
