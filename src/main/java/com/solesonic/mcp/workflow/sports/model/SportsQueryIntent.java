package com.solesonic.mcp.workflow.sports.model;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Parsed result of the user's NBA question — extracted by the LLM from natural language.
 *
 * @param questionTypes one or more categories describing the question (multiple are valid)
 * @param teams         full team names mentioned (e.g. "Boston Celtics", not "Celtics")
 * @param players       player names mentioned
 * @param focusPlayer   single player name when the question is specifically about one player; null otherwise
 */
public record SportsQueryIntent(
        List<String> questionTypes,
        List<String> teams,
        List<String> players,
        String focusPlayer
) {
    public SportsQuestionType resolvedQuestionType() {
        if (CollectionUtils.isEmpty(questionTypes)) {
            return SportsQuestionType.GENERAL_NEWS;
        }

        try {
            return SportsQuestionType.valueOf(questionTypes.getFirst().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SportsQuestionType.GENERAL_NEWS;
        }
    }

    public boolean hasTeams() {
        return CollectionUtils.isNotEmpty(teams);
    }

    public boolean hasPlayers() {
        return CollectionUtils.isNotEmpty(players);
    }

    public boolean hasFocusPlayer() {
        return StringUtils.isNotEmpty(focusPlayer);
    }
}
