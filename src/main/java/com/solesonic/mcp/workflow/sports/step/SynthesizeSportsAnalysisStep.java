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
public class SynthesizeSportsAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "synthesize-sports-analysis";

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisStep.class);

    @Value("classpath:prompt/sports/synthesize-sports-analysis.st")
    private Resource synthesizeSportAnalysisResource;

    private final ChatClient chatClient;

    public SynthesizeSportsAnalysisStep(@Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext context, WorkflowExecutionContext executionContext) {
        context.setCurrentStage(SportsWorkflowStage.SYNTHESIZING_ANALYSIS);
        executionContext.progressTracker().step(name()).update(0.1, "Sportsball sprockets synthesizing");

        SportsQueryIntent intent = context.getSportsQueryIntent();
        String questionType = intent.questionTypes().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));

        String scheduleResults = context.getScheduleSearchSummary() != null
                ? context.getScheduleSearchSummary()
                : "No schedule data gathered.";

        String newsResults = context.getNewsSearchSummary() != null
                ? context.getNewsSearchSummary()
                : "No news data gathered.";

        String statsResults = context.getStatisticsSearchSummary() != null
                ? context.getStatisticsSearchSummary()
                : "Statistics not applicable for this query type.";

        String todayDate = todayDate();

        PromptTemplate synthesizeSportAnalysisTemplate = new PromptTemplate(synthesizeSportAnalysisResource);

        Map<String, Object> promptVars = Map.of(
                USER_MESSAGE, context.getOriginalUserMessage(),
                TODAY_DATE, todayDate,
                QUESTION_TYPE, questionType,
                SCHEDULE_RESULTS, scheduleResults,
                NEWS_RESULTS, newsResults,
                STATS_RESULTS, statsResults,
                NBA_TERMINOLOGY, NBA_TERMINOLOGY_CONTENT
        );

        Prompt synthesizeSportAnalysisPrompt = synthesizeSportAnalysisTemplate.create(promptVars);

        log.info("Synthesizing sports analysis for question type: {}", questionType);
        executionContext.progressTracker().step(name()).update(0.5, "Sportsball droid still shooting");

        String analysis = chatClient.prompt(synthesizeSportAnalysisPrompt)
                .call()
                .content();

        context.setFinalAnalysis(analysis);

        log.info("Sports analysis generated");

        executionContext.progressTracker().step(name()).done("Sportsball robot finished.... ahhhh");

        return WorkflowDecision.continueWorkflow();
    }
}
