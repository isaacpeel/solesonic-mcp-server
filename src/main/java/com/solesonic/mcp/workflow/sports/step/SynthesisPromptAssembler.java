package com.solesonic.mcp.workflow.sports.step;

import com.solesonic.mcp.workflow.sports.SportsResearchWorkflowContext;
import com.solesonic.mcp.workflow.sports.model.SportsQueryIntent;
import com.solesonic.mcp.workflow.sports.model.SportsQuestionType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.solesonic.mcp.prompt.PromptConstants.TODAY_DATE;
import static com.solesonic.mcp.prompt.PromptConstants.USER_MESSAGE;
import static com.solesonic.mcp.prompt.PromptConstants.todayDate;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.GAME_PREVIEW;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.GENERAL_NEWS;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.PLAYER_ANALYSIS;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.SCHEDULE_LOOKUP;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.STANDINGS;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.STATISTICS;
import static com.solesonic.mcp.workflow.sports.model.SportsQuestionType.TRADE_NEWS;
import static com.solesonic.mcp.workflow.sports.step.SynthesisSection.NEWS;
import static com.solesonic.mcp.workflow.sports.step.SynthesisSection.SCHEDULE;
import static com.solesonic.mcp.workflow.sports.step.SynthesisSection.STATS;
import static com.solesonic.mcp.workflow.sports.step.SynthesisSection.TERMINOLOGY;

@Component
public class SynthesisPromptAssembler {

    private static final Map<SportsQuestionType, Set<SynthesisSection>> INTENT_SECTIONS;

    static {
        INTENT_SECTIONS = new EnumMap<>(SportsQuestionType.class);
        INTENT_SECTIONS.put(SCHEDULE_LOOKUP, EnumSet.of(SCHEDULE));
        INTENT_SECTIONS.put(GAME_PREVIEW, EnumSet.of(SCHEDULE, NEWS));
        INTENT_SECTIONS.put(PLAYER_ANALYSIS, EnumSet.of(STATS, NEWS, TERMINOLOGY));
        INTENT_SECTIONS.put(STANDINGS, EnumSet.of(STATS));
        INTENT_SECTIONS.put(GENERAL_NEWS, EnumSet.of(NEWS));
        INTENT_SECTIONS.put(STATISTICS, EnumSet.of(STATS, TERMINOLOGY));
        INTENT_SECTIONS.put(TRADE_NEWS, EnumSet.of(NEWS));
    }

    @Value("classpath:prompt/sports/synthesize-header.st")
    private Resource headerResource;

    @Value("classpath:prompt/sports/instructions-schedule-lookup.st")
    private Resource scheduleLookupInstructionsResource;

    @Value("classpath:prompt/sports/instructions-game-preview.st")
    private Resource gamePreviewInstructionsResource;

    @Value("classpath:prompt/sports/instructions-player-analysis.st")
    private Resource playerAnalysisInstructionsResource;

    @Value("classpath:prompt/sports/instructions-standings.st")
    private Resource standingsInstructionsResource;

    @Value("classpath:prompt/sports/instructions-general-news.st")
    private Resource generalNewsInstructionsResource;

    @Value("classpath:prompt/sports/instructions-statistics.st")
    private Resource statisticsInstructionsResource;

    @Value("classpath:prompt/sports/instructions-trade-news.st")
    private Resource tradeNewsInstructionsResource;

    @Value("classpath:prompt/sports/instructions-general.st")
    private Resource generalInstructionsResource;

    public Prompt assemble(SportsResearchWorkflowContext workflowContext) {
        SportsQueryIntent sportsQueryIntent = workflowContext.getSportsQueryIntent();
        assert sportsQueryIntent != null;

        Set<SynthesisSection> sections = resolveSections(sportsQueryIntent);
        Resource instructionsResource = resolveInstructionsResource(sportsQueryIntent);

        String assembledTemplate = buildTemplateString(sections, instructionsResource);
        Map<String, Object> variables = buildVariableMap(sections, workflowContext);

        return new PromptTemplate(assembledTemplate).create(variables);
    }

    private String buildTemplateString(Set<SynthesisSection> sections, Resource instructionsResource) {
        StringBuilder templateBuilder = new StringBuilder(readResource(headerResource));
        for (SynthesisSection section : sections) {
            templateBuilder.append(section.getFragmentTemplate());
        }
        templateBuilder.append("=============================================\n\n");
        templateBuilder.append(readResource(instructionsResource));
        return templateBuilder.toString();
    }

    private Map<String, Object> buildVariableMap(Set<SynthesisSection> sections, SportsResearchWorkflowContext workflowContext) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(USER_MESSAGE, workflowContext.userMessage());
        variables.put(TODAY_DATE, todayDate());
        for (SynthesisSection section : sections) {
            if (section.hasVariable()) {
                variables.put(section.getVariableKey(), resolveVariableValue(section, workflowContext));
            }
        }
        return variables;
    }

    private Set<SynthesisSection> resolveSections(SportsQueryIntent sportsQueryIntent) {
        if (sportsQueryIntent.questionTypes().size() == 1) {
            SportsQuestionType singleType = sportsQueryIntent.questionTypes().getFirst();
            return INTENT_SECTIONS.getOrDefault(singleType, EnumSet.allOf(SynthesisSection.class));
        }
        Set<SynthesisSection> unionSections = EnumSet.noneOf(SynthesisSection.class);
        for (SportsQuestionType questionType : sportsQueryIntent.questionTypes()) {
            Set<SynthesisSection> typeSections = INTENT_SECTIONS.get(questionType);
            if (typeSections != null) {
                unionSections.addAll(typeSections);
            }
        }
        return unionSections.isEmpty() ? EnumSet.allOf(SynthesisSection.class) : unionSections;
    }

    private Resource resolveInstructionsResource(SportsQueryIntent sportsQueryIntent) {
        if (sportsQueryIntent.questionTypes().size() != 1) {
            return generalInstructionsResource;
        }
        return switch (sportsQueryIntent.questionTypes().getFirst()) {
            case SCHEDULE_LOOKUP -> scheduleLookupInstructionsResource;
            case GAME_PREVIEW -> gamePreviewInstructionsResource;
            case PLAYER_ANALYSIS -> playerAnalysisInstructionsResource;
            case STANDINGS -> standingsInstructionsResource;
            case GENERAL_NEWS -> generalNewsInstructionsResource;
            case STATISTICS -> statisticsInstructionsResource;
            case TRADE_NEWS -> tradeNewsInstructionsResource;
        };
    }

    private String resolveVariableValue(SynthesisSection section, SportsResearchWorkflowContext workflowContext) {
        return switch (section) {
            case SCHEDULE -> Objects.requireNonNullElse(workflowContext.getScheduleSearchSummary(), "--NO RESULTS--");
            case NEWS -> Objects.requireNonNullElse(workflowContext.getNewsSearchSummary(), "--NO RESULTS--");
            case STATS -> Objects.requireNonNullElse(workflowContext.getStatisticsSearchSummary(), "--NO RESULTS--");
            case TERMINOLOGY -> throw new IllegalStateException("TERMINOLOGY has no variable and should never be resolved");
        };
    }

    private String readResource(Resource resource) {
        try {
            return resource.getContentAsString(Charset.defaultCharset());
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to read prompt resource: " + resource.getDescription(), ioException);
        }
    }
}
