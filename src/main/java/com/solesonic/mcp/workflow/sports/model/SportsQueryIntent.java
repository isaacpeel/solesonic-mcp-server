package com.solesonic.mcp.workflow.sports.model;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Parsed result of the user's NBA question — extracted by the LLM from natural language.
 *
 * @param questionType the category of NBA question
 * @param teams        full NBA team names mentioned (e.g. "Boston Celtics", not "Celtics")
 * @param players      player names mentioned
 * @param timeContext  temporal context: "today", "upcoming", "recent", "season", or "specific: YYYY-MM-DD"
 */
public record SportsQueryIntent(
        SportsQuestionType questionType,
        List<String> teams,
        List<String> players,
        String timeContext
) {

    public boolean hasTeams() {
        return CollectionUtils.isNotEmpty(teams);
    }

    public boolean hasPlayers() {
        return CollectionUtils.isNotEmpty(players);
    }
}
