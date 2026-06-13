package com.solesonic.agent.sports.node;

import com.solesonic.agent.sports.SportsState;
import com.solesonic.agent.sports.model.SportsQueryIntent;
import com.solesonic.agent.sports.model.SportsQuestionType;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.solesonic.agent.sports.model.SportsQuestionType.COACHING;
import static com.solesonic.agent.sports.model.SportsQuestionType.DRAFT;
import static com.solesonic.agent.sports.model.SportsQuestionType.GAME_PREVIEW;
import static com.solesonic.agent.sports.model.SportsQuestionType.GENERAL_NEWS;
import static com.solesonic.agent.sports.model.SportsQuestionType.HISTORICAL;
import static com.solesonic.agent.sports.model.SportsQuestionType.INJURY_REPORT;
import static com.solesonic.agent.sports.model.SportsQuestionType.PLAYER_ANALYSIS;
import static com.solesonic.agent.sports.model.SportsQuestionType.SCHEDULE_LOOKUP;
import static com.solesonic.agent.sports.model.SportsQuestionType.STANDINGS;
import static com.solesonic.agent.sports.model.SportsQuestionType.STATISTICS;
import static com.solesonic.agent.sports.model.SportsQuestionType.TRADE_NEWS;

/**
 * Runs the relevant sub-graphs concurrently for multi-type queries.
 * Each unique sub-graph is executed with a no-op SynthesisOutputEmitter to suppress streaming.
 * The FINAL_ANALYSIS from each sub-graph is collected into SUB_GRAPH_RESULTS for MetaSynthesizeNode.
 */
@Component
public class FanOutNode implements AsyncNodeActionWithConfig<SportsState> {

    private static final Logger log = LoggerFactory.getLogger(FanOutNode.class);

    private static final Map<SportsQuestionType, String> TYPE_TO_SUB_GRAPH_NAME = Map.ofEntries(
            Map.entry(SCHEDULE_LOOKUP,  "schedule"),
            Map.entry(STANDINGS,        "standings"),
            Map.entry(GENERAL_NEWS,     "news"),
            Map.entry(TRADE_NEWS,       "news"),
            Map.entry(INJURY_REPORT,    "news"),
            Map.entry(DRAFT,            "news"),
            Map.entry(COACHING,         "news"),
            Map.entry(STATISTICS,       "stats"),
            Map.entry(HISTORICAL,       "stats"),
            Map.entry(GAME_PREVIEW,     "gamePreview"),
            Map.entry(PLAYER_ANALYSIS,  "player")
    );

    private final Map<String, CompiledGraph<SportsState>> subGraphsByName;

    public FanOutNode(
            @Qualifier("nbaScheduleGraph")    CompiledGraph<SportsState> nbaScheduleGraph,
            @Qualifier("nbaStandingsGraph")   CompiledGraph<SportsState> nbaStandingsGraph,
            @Qualifier("nbaNewsGraph")        CompiledGraph<SportsState> nbaNewsGraph,
            @Qualifier("nbaStatsGraph")       CompiledGraph<SportsState> nbaStatsGraph,
            @Qualifier("nbaGamePreviewGraph") CompiledGraph<SportsState> nbaGamePreviewGraph,
            @Qualifier("nbaPlayerGraph")      CompiledGraph<SportsState> nbaPlayerGraph
    ) {
        this.subGraphsByName = Map.of(
                "schedule",    nbaScheduleGraph,
                "standings",   nbaStandingsGraph,
                "news",        nbaNewsGraph,
                "stats",       nbaStatsGraph,
                "gamePreview", nbaGamePreviewGraph,
                "player",      nbaPlayerGraph
        );
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(SportsState state, RunnableConfig parentConfig) {
        List<SportsQuestionType> questionTypes = state.sportsQueryIntent()
                .map(SportsQueryIntent::questionTypes)
                .orElse(List.of());

        // Map question types to unique sub-graph names, preserving insertion order
        LinkedHashSet<String> targetSubGraphNames = new LinkedHashSet<>();
        for (SportsQuestionType questionType : questionTypes) {
            String subGraphName = TYPE_TO_SUB_GRAPH_NAME.get(questionType);
            if (subGraphName != null) {
                targetSubGraphNames.add(subGraphName);
            }
        }

        if (targetSubGraphNames.isEmpty()) {
            log.warn("FanOutNode: no sub-graphs matched for question types: {}", questionTypes);
            return CompletableFuture.completedFuture(Map.of());
        }

        // Suppress streaming during fan-out — each sub-graph's FINAL_ANALYSIS is collected directly
        RunnableConfig noOpConfig = RunnableConfig.builder(parentConfig)
                .addMetadata(SynthesisOutputEmitter.CONFIG_KEY, SynthesisOutputEmitter.noOp())
                .build();

        Map<String, Object> inputState = state.data();

        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();

        for (String subGraphName : targetSubGraphNames) {
            CompiledGraph<SportsState> subGraph = subGraphsByName.get(subGraphName);

            AtomicReference<SportsState> finalStateRef = new AtomicReference<>();

            CompletableFuture<Map.Entry<String, String>> future = subGraph
                    .stream(inputState, noOpConfig)
                    .forEachAsync(output -> finalStateRef.set(output.state()))
                    .thenApply(ignored -> {
                        SportsState finalState = finalStateRef.get();
                        String analysis = finalState != null
                                ? finalState.finalAnalysis().orElse("No analysis produced.")
                                : "No analysis produced.";
                        return Map.entry(subGraphName, analysis);
                    })
                    .exceptionally(exception -> {
                        log.warn("Sub-graph '{}' failed during fan-out: {}", subGraphName, exception.getMessage());
                        return Map.entry(subGraphName, "Analysis unavailable for this topic.");
                    });

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Map<String, String> subGraphResults = new LinkedHashMap<>();
                    for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                        Map.Entry<String, String> result = future.join();
                        subGraphResults.put(result.getKey(), result.getValue());
                    }
                    return Map.of(SportsState.SUB_GRAPH_RESULTS, subGraphResults);
                });
    }
}
