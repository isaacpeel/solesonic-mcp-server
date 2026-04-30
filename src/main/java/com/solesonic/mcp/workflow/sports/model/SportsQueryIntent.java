package com.solesonic.mcp.workflow.sports.model;

import java.util.List;

/**
 * Parsed result of the user's sports question — extracted by the LLM from natural language.
 *
 * @param questionType the category of sports question
 * @param teams        full team names mentioned (e.g. "Boston Celtics", not "Celtics")
 * @param players      player names mentioned
 * @param focusPlayer  single player name when the question is specifically about one player; null otherwise
 * @param sport        sport type (e.g. "basketball", "football", "baseball")
 * @param league       league name (e.g. "NBA", "NFL", "MLB", "NHL")
 * @param timeContext  temporal context: "today", "upcoming", "recent", "season", or "specific: YYYY-MM-DD"
 */
public record SportsQueryIntent(
        String questionType,
        List<String> teams,
        List<String> players,
        String focusPlayer,
        String sport,
        String league,
        String timeContext
) {
    public SportsQuestionType resolvedQuestionType() {
        if (questionType == null) {
            return SportsQuestionType.GENERAL_NEWS;
        }
        try {
            return SportsQuestionType.valueOf(questionType.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SportsQuestionType.GENERAL_NEWS;
        }
    }

    public boolean hasTeams() {
        return teams != null && !teams.isEmpty();
    }

    public boolean hasPlayers() {
        return players != null && !players.isEmpty();
    }

    public boolean hasFocusPlayer() {
        return focusPlayer != null && !focusPlayer.isBlank();
    }
}
