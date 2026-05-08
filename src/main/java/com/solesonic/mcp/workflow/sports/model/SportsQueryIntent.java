package com.solesonic.mcp.workflow.sports.model;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * Parsed result of the user's NBA question — extracted by the LLM from natural language.
 *
 * @param questionTypes the category of NBA questions
 * @param teams        full NBA team names mentioned (e.g. "Boston Celtics", not "Celtics")
 * @param players      player names mentioned
 * @param timeContext  temporal context: "today", "upcoming", "recent", "season", or "specific: YYYY-MM-DD"
 */
public record SportsQueryIntent(
        List<SportsQuestionType> questionTypes,
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

    public boolean hasFocusPlayer() {
        return CollectionUtils.size(players) == 1;
    }

    public String focusPlayer() {
        return hasFocusPlayer() ? players.getFirst() : null;
    }
}
