package com.solesonic.a2a.workflow.sports.step;

import com.solesonic.a2a.workflow.framework.WorkflowDecision;
import com.solesonic.a2a.workflow.framework.WorkflowExecutionContext;
import com.solesonic.a2a.workflow.framework.WorkflowStep;
import com.solesonic.a2a.workflow.sports.SportsResearchWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.solesonic.a2a.workflow.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT;
import static com.solesonic.a2a.workflow.sports.SportsWorkflowStage.SYNTHESIZING_ANALYSIS;

@Component
public class SynthesizeSportsAnalysisStep implements WorkflowStep<SportsResearchWorkflowContext> {
    public static final String STEP_NAME = "synthesize-sports-analysis";

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisStep.class);

    private final ChatClient chatClient;
    private final SynthesisPromptAssembler synthesisPromptAssembler;

    public SynthesizeSportsAnalysisStep(
            @Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient,
            SynthesisPromptAssembler synthesisPromptAssembler) {
        this.chatClient = chatClient;
        this.synthesisPromptAssembler = synthesisPromptAssembler;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public WorkflowDecision execute(SportsResearchWorkflowContext sportsResearchWorkflowContext, WorkflowExecutionContext workflowExecutionContext) {
        sportsResearchWorkflowContext.setCurrentStage(SYNTHESIZING_ANALYSIS);

        workflowExecutionContext.progressTracker().step(name()).update(0.1, "Sportsball sprockets synthesizing");

        Prompt synthesisPrompt = synthesisPromptAssembler.assemble(sportsResearchWorkflowContext);

        log.info("Synthesizing sports analysis for intent: {}", sportsResearchWorkflowContext.getSportsQueryIntent().questionTypes());
        workflowExecutionContext.progressTracker().step(name()).update(0.5, "Sportsball droid still shooting");

        String analysis = chatClient.prompt(synthesisPrompt)
                .call()
                .content();

        sportsResearchWorkflowContext.setFinalAnalysis(analysis);

        log.info("Sports analysis generated");

        workflowExecutionContext.progressTracker().step(name()).done("Sportsball robot finished.... ahhhh");

        return WorkflowDecision.continueWorkflow();
    }
}
