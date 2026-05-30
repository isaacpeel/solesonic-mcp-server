package com.solesonic.agent.sports.model;

import java.util.List;

/**
 * Entity extraction result from the LLM — contains only the entities pulled from the user
 * message and conversation history, not the intent classification (which is done by keyword rules).
 */
public record SportsEntityExtraction(
        List<String> teams,
        List<String> players,
        String timeContext
) {}
