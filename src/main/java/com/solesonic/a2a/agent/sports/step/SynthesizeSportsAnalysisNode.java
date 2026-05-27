package com.solesonic.a2a.agent.sports.step;

import com.solesonic.a2a.agent.sports.SportsState;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.solesonic.a2a.agent.sports.SportsChatClientConfig.SPORTS_CHAT_CLIENT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Component
public class SynthesizeSportsAnalysisNode implements AsyncNodeAction<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSportsAnalysisNode.class);

    private final ChatClient chatClient;
    private final SynthesisPromptAssembler synthesisPromptAssembler;

    public SynthesizeSportsAnalysisNode(
            @Qualifier(SPORTS_CHAT_CLIENT) ChatClient chatClient,
            SynthesisPromptAssembler synthesisPromptAssembler) {
        this.chatClient = chatClient;
        this.synthesisPromptAssembler = synthesisPromptAssembler;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state) {
        try {
            Prompt synthesisPrompt = synthesisPromptAssembler.assemble(state);
            Optional<String> conversationId = state.conversationId();

            log.info("Synthesizing sports analysis for intent: {}",
                    state.sportsQueryIntent()
                            .map(intent -> intent.questionTypes().toString())
                            .orElse("unknown"));

            String analysis = chatClient.prompt(synthesisPrompt)
                    .advisors(advisorSpec -> conversationId.ifPresent(id -> advisorSpec.param(CONVERSATION_ID, id)))
                    .call()
                    .content();

            log.info("Sports analysis generated");

            assert analysis != null;
            return completedFuture(Map.of(SportsState.FINAL_ANALYSIS, analysis));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }
}
