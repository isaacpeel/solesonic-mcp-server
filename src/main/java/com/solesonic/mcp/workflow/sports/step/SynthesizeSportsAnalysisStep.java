package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.framework.WorkflowDecision;
import com.solesonic.mcp.workflow.framework.WorkflowExecutionContext;
import com.solesonic.mcp.workflow.framework.WorkflowStep;
import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

import static com.solesonic.mcp.prompt.PromptConstants.*;
import static com.solesonic.mcp.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT;
import static com.solesonic.mcp.workflow.sports.SportsWorkflowStage.SYNTHESIZING_ANALYSIS;

@Component
public class SynthesizeSportsAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "synthesize-sports-analysis";

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisStep.class);
    public static final String NO_RESULTS = "--NO RESULTS--";

    @Value("classpath:prompt/sports/synthesize-sports-analysis.st")
    private Resource synthesizeSportAnalysisResource;

    @Value("classpath:prompt/sports/nba-terminology.md")
    private Resource nbaTerminologyResource;

    private final ChatClient chatClient;

    public SynthesizeSportsAnalysisStep(@Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext sportsResearchWorkflowContext, WorkflowExecutionContext workflowExecutionContext) {
        sportsResearchWorkflowContext.setCurrentStage(SYNTHESIZING_ANALYSIS);

        workflowExecutionContext.progressTracker().step(name()).update(0.1, "Sportsball sprockets synthesizing");

        SportsQueryIntent sportsQueryIntent = sportsResearchWorkflowContext.getSportsQueryIntent();


        assert sportsQueryIntent != null;
        String questionType = sportsQueryIntent.questionTypes().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));


        String scheduleResults = Objects.requireNonNullElse(sportsResearchWorkflowContext.getScheduleSearchSummary(), NO_RESULTS);
        String newsResults = Objects.requireNonNullElse(sportsResearchWorkflowContext.getNewsSearchSummary(), NO_RESULTS);
        String statsResults = Objects.requireNonNullElse(sportsResearchWorkflowContext.getStatisticsSearchSummary(), NO_RESULTS);

        String todayDate = todayDate();
        PromptTemplate synthesizeSportAnalysisTemplate = new PromptTemplate(synthesizeSportAnalysisResource);

        String nbaTerminology;

        try {
            nbaTerminology = nbaTerminologyResource.getContentAsString(Charset.defaultCharset());
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }

        Map<String, Object> promptVars = Map.of(
                USER_MESSAGE, sportsResearchWorkflowContext.userMessage(),
                TODAY_DATE, todayDate,
                QUESTION_TYPE, questionType,
                SCHEDULE_RESULTS, scheduleResults,
                NEWS_RESULTS, newsResults,
                STATS_RESULTS, statsResults,
                NBA_TERMINOLOGY, nbaTerminology
        );

        Prompt synthesizeSportAnalysisPrompt = synthesizeSportAnalysisTemplate.create(promptVars);

        log.info("Synthesizing sports analysis for question type: {}", questionType);
        workflowExecutionContext.progressTracker().step(name()).update(0.5, "Sportsball droid still shooting");

        String analysis = chatClient.prompt(synthesizeSportAnalysisPrompt)
                .call()
                .content();

        sportsResearchWorkflowContext.setFinalAnalysis(analysis);

        log.info("Sports analysis generated");

        workflowExecutionContext.progressTracker().step(name()).done("Sportsball robot finished.... ahhhh");

        return WorkflowDecision.continueWorkflow();
    }
}
